package top.fpsmaster.replay

import com.mojang.authlib.GameProfile
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.replay.adapter.DirectorRenderAdapter
import top.fpsmaster.replay.adapter.ReplayPacketAdapter
import top.fpsmaster.replay.adapter.ReplayWorldAdapter
import java.io.File

/**
 * Plays a recording back on the client thread, into an isolated listener.
 *
 * No server is started, in memory or otherwise: recorded bytes are decoded with this version's own
 * codec and handed straight to a [ReplayWorldAdapter.IsolatedSession], which is where a connection
 * would have handed them over anyway. The vanilla handlers then build the world exactly as they did
 * while it was being recorded.
 *
 * Records are held in memory. Seeking backwards means tearing the world down and replaying from the
 * start — a packet stream has no other way back, since any packet may be the one that placed the
 * block you are looking at — and a stream that had to be re-read from disk for every scrub would
 * make the director unusable.
 */
class ReplayPlayback private constructor(
    val header: NovaReplayFile.Header,
    private val records: List<ReplayRecord>,
) {

    /** Position on the recording's own timeline. */
    var positionMillis: Int = 0
        private set

    var isPaused: Boolean = true
        private set

    var speed: Float = 1f
        set(value) {
            field = value.coerceIn(SPEED_MIN, SPEED_MAX)
        }

    val durationMillis: Int = records.lastOrNull()?.millis ?: 0

    /** True while playback owns the client's world. */
    val isOpen: Boolean
        get() = session != null

    private var session: ReplayWorldAdapter.IsolatedSession? = null
    private var cursor = 0
    private var lastFrameNanos = 0L

    /** Set when the recorded player's own position should drive the camera. */
    var followRecorder: Boolean = true

    /**
     * Opens the world and applies everything stamped at time zero — the session packets and the
     * terrain snapshot — before the first frame is drawn.
     */
    fun open() {
        if (session != null) {
            return
        }
        val profile = GameProfile(header.profile.recorderId, header.profile.recorderName)
        session = ReplayWorldAdapter.open(profile)
        cursor = 0
        positionMillis = 0
        lastFrameNanos = System.nanoTime()
        applyUntil(0)
    }

    fun play() {
        isPaused = false
        lastFrameNanos = System.nanoTime()
    }

    fun pause() {
        isPaused = true
    }

    /** Client thread, once per frame. Advances the clock and applies whatever is now due. */
    fun tick() {
        val active = session ?: return
        active.discardOutbound()
        if (isPaused) {
            lastFrameNanos = System.nanoTime()
            return
        }
        val now = System.nanoTime()
        val deltaMillis = ((now - lastFrameNanos) / 1_000_000L * speed).toInt()
        lastFrameNanos = now
        if (deltaMillis <= 0) {
            return
        }
        applyUntil(positionMillis + deltaMillis)
        if (positionMillis >= durationMillis) {
            pause()
        }
    }

    /**
     * Moves to [millis]. Forwards this replays the packets in between as fast as it can; backwards
     * it rebuilds the world from the start, because a packet stream cannot be run in reverse.
     */
    fun seekTo(millis: Int) {
        val target = millis.coerceIn(0, durationMillis)
        if (target < positionMillis) {
            rebuild()
        }
        applyUntil(target)
    }

    fun close() {
        session?.let { ReplayWorldAdapter.close(it) }
        session = null
        DirectorRenderAdapter.clear()
    }

    /** The recorder's pose at [millis], for the camera to follow and for keyframing against. */
    fun recorderPoseAt(millis: Int): ReplayRecord? = records
        .lastOrNull { it.type == NovaReplayFile.TYPE_LOCAL_PLAYER && it.millis <= millis }

    private fun rebuild() {
        val active = session ?: return
        ReplayWorldAdapter.close(active)
        session = null
        open()
    }

    private fun applyUntil(target: Int) {
        val active = session ?: return
        val registries = active.registries
        while (cursor < records.size && records[cursor].millis <= target) {
            apply(active, records[cursor], registries)
            cursor++
        }
        positionMillis = target
    }

    private fun apply(
        active: ReplayWorldAdapter.IsolatedSession,
        record: ReplayRecord,
        registries: net.minecraft.core.RegistryAccess,
    ) {
        if (record.type == NovaReplayFile.TYPE_LOCAL_PLAYER) {
            applyRecorderPose(record)
            return
        }
        val payload = record.payload ?: return
        val packet = ReplayPacketAdapter.decode(payload, registries) ?: return
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

        /**
         * Reads a recording into memory.
         *
         * @throws ReplayVersionMismatchException the file was recorded on another Minecraft version
         * @throws ReplayFormatException it is not a recording this build can read
         */
        fun load(file: File): ReplayPlayback {
            val reader = NovaReplayFile.openForRead(file, ReplayPacketAdapter.minecraftVersion)
            val records = ArrayList<ReplayRecord>()
            reader.use {
                var bytes = 0L
                while (true) {
                    val record = it.read() ?: break
                    records.add(record)
                    bytes += (record.payload?.size ?: 0).toLong()
                    if (bytes > MAX_LOADED_BYTES) {
                        logger.warn(
                            "[replay] ${file.name} is larger than ${MAX_LOADED_BYTES shr 20}MB;" +
                                " playing back the first ${records.size} records"
                        )
                        break
                    }
                }
            }
            return ReplayPlayback(reader.header, records)
        }

        /** Header only, for listing recordings without reading them. */
        fun describe(file: File): NovaReplayFile.Header? = try {
            NovaReplayFile.readHeader(file)
        } catch (unreadable: Exception) {
            null
        }

        /** Ceiling on how much of a recording is held at once. */
        private const val MAX_LOADED_BYTES = 512L shl 20
    }
}
