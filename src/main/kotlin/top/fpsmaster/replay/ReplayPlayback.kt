package top.fpsmaster.replay

import com.mojang.authlib.GameProfile
import net.minecraft.client.gui.screens.TitleScreen
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.replay.adapter.DirectorRenderAdapter
import top.fpsmaster.replay.adapter.ReplayPacketAdapter
import top.fpsmaster.replay.adapter.ReplayWorldAdapter
import top.fpsmaster.screenCompat
import java.io.File

import net.minecraft.client.gui.screens.Screen
import top.fpsmaster.setScreenCompat

/**
 * Plays a recording back on the client thread, into an isolated listener.
 *
 * No server is started, in memory or otherwise: recorded bytes are decoded with this version's own
 * codec and handed straight to a [ReplayWorldAdapter.IsolatedSession], which is where a connection
 * would have handed them over anyway. The vanilla handlers then build the world exactly as they did
 * while it was being recorded.
 *
 * Nothing here holds the recording. A [ReplayStream] reads it on its own thread into a bounded
 * window ahead of the playhead, a [ReplayCursor] applies records from that window a time-budgeted
 * slice per tick, and the length comes from a [ReplayIndex] built on a background thread. Opening,
 * and seeking — which backwards still means rebuilding the world from the start, since a packet
 * stream cannot run in reverse — therefore never stall a frame; until the world has caught up with
 * the playhead [isLoading] says so, and the clock waits.
 */
class ReplayPlayback private constructor(
    private val file: File,
    val header: NovaReplayFile.Header,
) {

    /** Position on the recording's own timeline: where playback is, or is loading towards. */
    val positionMillis: Int
        get() = cursor?.targetMillis ?: 0

    var isPaused: Boolean = true
        private set

    var speed: Float = 1f
        set(value) {
            field = value.coerceIn(SPEED_MIN, SPEED_MAX)
        }

    /** Length of the recording; 0 until the background index lands, see [isIndexed]. */
    @Volatile
    var durationMillis: Int = 0
        private set

    @Volatile
    var isIndexed: Boolean = false
        private set

    /** True while playback owns the client's world. */
    val isOpen: Boolean
        get() = session != null

    /**
     * The world is behind the playhead, or a loading screen vanilla put up while rebuilding it is
     * still waiting to be taken down. Nothing on screen is the playhead's moment yet.
     */
    val isCatchingUp: Boolean
        get() = cursor?.isCaughtUp == false || restorePending

    /** Behind by enough to be worth telling the viewer, rather than a tick's worth of packets. */
    val isLoading: Boolean
        get() {
            val active = cursor ?: return false
            return restorePending || active.targetMillis - active.reachedMillis > LOADING_THRESHOLD_MILLIS
        }

    /** How far loading has got towards the playhead, 0..1. */
    val loadingProgress: Float
        get() {
            val active = cursor ?: return 0f
            if (active.targetMillis <= 0) {
                return if (active.isCaughtUp) 1f else 0f
            }
            return (active.appliedMillis.coerceAtLeast(0).toFloat() / active.targetMillis).coerceIn(0f, 1f)
        }

    private var session: ReplayWorldAdapter.IsolatedSession? = null
    private var cursor: ReplayCursor? = null
    private var lastFrameNanos = 0L
    private var lastPumpNanos = 0L

    @Volatile
    private var indexCancelled = false

    /**
     * Opening and rebuilding go through vanilla's login handling, which puts up a level-loading
     * screen. Once the world has caught up it is replaced by [restoreScreen]: the world itself after
     * opening, whatever was open before a rebuild (the replay controls, mid-scrub).
     */
    private var restorePending = false
    private var restoreScreen: Screen? = null

    /** Set when the recorded player's own position should drive the camera. */
    var followRecorder: Boolean = true

    /**
     * Leaves whatever world is open and starts loading this one: the session packets and terrain
     * snapshot stamped at time zero arrive over the next ticks, behind a loading screen.
     */
    fun open() {
        if (session != null) {
            return
        }
        leaveCurrentSession()
        startIsolatedSession()
        cursor = ReplayCursor(::openStream, ::applyRecord, ::rebuild)
        lastFrameNanos = System.nanoTime()
        lastPumpNanos = lastFrameNanos
        armRestore(null)
        startIndexing()
    }

    /**
     * Drops the live singleplayer or multiplayer session before the isolated replay listener
     * takes over. Opening playback on top of a live connection leaves that channel sending
     * movement, which looks like flight on the real server.
     *
     * Seek rebuilds go through [startIsolatedSession] only. The isolated listener is not a
     * live server and must not be disconnected with a menu in between.
     */
    private fun leaveCurrentSession() {
        if (mc.level == null && mc.player == null && mc.connection == null) {
            return
        }
        logger.info("[replay] leaving current world/server before playback")
        try {
            val screen = mc.screenCompat ?: TitleScreen()
            //? if >=1.20.5 {
            mc.disconnect(screen, false)
            //?} else {
            /*mc.clearLevel(screen)
            *///?}
        } catch (failure: Exception) {
            logger.warn("[replay] failed to leave current world/server: ${failure.message}")
        }
    }

    private fun startIsolatedSession() {
        val profile = GameProfile(header.profile.recorderId, header.profile.recorderName)
        session = ReplayWorldAdapter.open(profile)
    }

    private fun openStream(): ReplayStream = ReplayStream(
        { NovaReplayFile.openForRead(file, header.minecraftVersion) },
        windowBytes(),
    )

    private fun startIndexing() {
        val worker = Thread({
            try {
                val index = NovaReplayFile.scanIndex(file) { indexCancelled }
                if (!indexCancelled) {
                    durationMillis = index.durationMillis
                    isIndexed = true
                    logger.info(
                        "[replay] indexed ${file.name}: ${index.recordCount} records," +
                            " ${index.durationMillis / 1000}s, ${index.blockCount} blocks"
                    )
                }
            } catch (failure: Exception) {
                logger.warn("[replay] could not index ${file.name}: ${failure.message}")
            }
        }, "nova-replay-index")
        worker.isDaemon = true
        worker.start()
    }

    fun play() {
        isPaused = false
        lastFrameNanos = System.nanoTime()
    }

    fun pause() {
        isPaused = true
    }

    /** Client thread, once per tick. Advances the clock and applies a slice of whatever is now due. */
    fun tick() {
        val active = session ?: return
        val playhead = cursor ?: return
        active.discardOutbound()
        val now = System.nanoTime()
        // The clock waits while the world catches up: otherwise the playhead would run on ahead
        // of a world that is still loading, and the viewer would miss whatever happened meanwhile.
        if (isPaused || isCatchingUp) {
            lastFrameNanos = now
        } else {
            val deltaMillis = ((now - lastFrameNanos) / 1_000_000L * speed).toInt()
            lastFrameNanos = now
            if (deltaMillis > 0) {
                var target = playhead.targetMillis + deltaMillis
                if (isIndexed) {
                    target = minOf(target, durationMillis)
                }
                playhead.seekTo(target)
            }
        }
        pump()
        if (!isPaused && !isCatchingUp &&
            (playhead.isAtEnd || (isIndexed && playhead.targetMillis >= durationMillis))
        ) {
            pause()
        }
    }

    /**
     * Moves to [millis]. Forwards this applies the packets in between; backwards it rebuilds the
     * world from the start, because a packet stream cannot be run in reverse. Either way only a
     * budgeted slice runs now and [tick] carries on with the rest — see [isCatchingUp].
     */
    fun seekTo(millis: Int) {
        val playhead = cursor ?: return
        val target = if (isIndexed) millis.coerceIn(0, durationMillis) else millis.coerceAtLeast(0)
        playhead.seekTo(target)
        pump()
        // seek 花掉的墙钟不是「回放走过的时间」。不在这儿把时钟重新对齐的话，下一帧算出来的
        // delta 就是这段时间乘上 speed，播放头一步跨过好几个片段，又触发一次往回 seek。
        lastFrameNanos = System.nanoTime()
    }

    fun close() {
        indexCancelled = true
        cursor?.close()
        cursor = null
        session?.let { ReplayWorldAdapter.close(it) }
        session = null
        restorePending = false
        restoreScreen = null
        // reset(), not clear(): clear() deliberately keeps the hand-set roll (an empty camera track
        // runs through it every tick), and leaving the replay is where that tilt has to go.
        DirectorRenderAdapter.reset()
    }

    /**
     * Applies records for at most a share of the wall time since the last call, so catching up
     * after a seek leaves frames being drawn — including when the client runs several ticks in
     * one frame to catch up with itself, where the share comes out at the floor.
     */
    private fun pump() {
        val playhead = cursor ?: return
        val now = System.nanoTime()
        val budget = ((now - lastPumpNanos) * PUMP_SHARE_PERCENT / 100).coerceIn(PUMP_MIN_NANOS, PUMP_MAX_NANOS)
        lastPumpNanos = now
        playhead.pump(now + budget)
        restoreScreenWhenLoaded()
    }

    /** Cursor callback: the playhead went backwards, so the world starts over. */
    private fun rebuild() {
        armRestore(mc.screenCompat)
        session?.let { ReplayWorldAdapter.close(it) }
        session = null
        startIsolatedSession()
    }

    private fun armRestore(screen: Screen?) {
        // A rebuild while an earlier one is still loading keeps the first one's screen: what is
        // open now is the loading screen that rebuild put up.
        if (!restorePending) {
            restorePending = true
            restoreScreen = screen
        }
    }

    private fun restoreScreenWhenLoaded() {
        if (!restorePending || cursor?.isCaughtUp != true || mc.level == null) {
            return
        }
        restorePending = false
        val target = restoreScreen
        restoreScreen = null
        if (mc.screenCompat !== target) {
            mc.setScreenCompat(target)
        }
    }

    private fun applyRecord(record: ReplayRecord) {
        val active = session ?: return
        if (record.type == NovaReplayFile.TYPE_LOCAL_PLAYER) {
            applyRecorderPose(record)
            return
        }
        val payload = record.payload ?: return
        val packet = ReplayPacketAdapter.decode(payload, active.registries) ?: return
        try {
            ReplayWorldAdapter.dispatch(active, packet)
        } catch (failure: Throwable) {
            // One packet the client cannot apply is a hole in the picture, not the end of the
            // replay: a chunk that fails still leaves every other chunk standing.
            logger.debug("[replay] ${packet.javaClass.simpleName} could not be applied: ${failure.message}")
        }
    }

    /** The recording client's own movement, which no clientbound packet carries. */
    private fun applyRecorderPose(record: ReplayRecord) {
        if (!followRecorder || DirectorRenderAdapter.isDriving) {
            return
        }
        val player = mc.player ?: return
        player.setPos(record.x, record.y, record.z)
        player.yRot = record.yaw
        player.xRot = record.pitch
        player.yRotO = record.yaw
        player.xRotO = record.pitch
        player.isSprinting = record.flags and NovaReplayFile.FLAG_SPRINTING != 0
        player.isShiftKeyDown = record.flags and NovaReplayFile.FLAG_SNEAKING != 0
    }

    companion object {
        const val SPEED_MIN: Float = 0.1f
        const val SPEED_MAX: Float = 8f

        /** Behind the playhead by more than this, the overlay shows loading instead of nothing. */
        private const val LOADING_THRESHOLD_MILLIS = 250

        private const val PUMP_SHARE_PERCENT = 60L
        private const val PUMP_MIN_NANOS = 2_000_000L
        private const val PUMP_MAX_NANOS = 40_000_000L

        /**
         * Opens a recording for playback. Reads the header only — the records stream in once
         * [open] is called.
         *
         * @throws ReplayVersionMismatchException the file was recorded on another Minecraft version
         * @throws ReplayFormatException it is not a recording this build can read
         */
        fun load(file: File): ReplayPlayback {
            val header = NovaReplayFile.openForRead(file, ReplayPacketAdapter.minecraftVersion).use { it.header }
            return ReplayPlayback(file, header)
        }

        /** Header only, for listing recordings without reading them. */
        fun describe(file: File): NovaReplayFile.Header? = try {
            NovaReplayFile.readHeader(file)
        } catch (unreadable: Exception) {
            null
        }

        /**
         * How much of a recording is read ahead of the playhead: a thirty-second of the heap, between
         * 8 and 64 MB. Whatever the recording's length, this is all of it that is in memory at once.
         */
        fun windowBytes(): Long = (Runtime.getRuntime().maxMemory() / 32).coerceIn(8L shl 20, 64L shl 20)
    }
}
