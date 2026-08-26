package top.fpsmaster.replay

import net.minecraft.network.protocol.Packet
import top.fpsmaster.logger
import top.fpsmaster.replay.adapter.ReplayPacketAdapter
import top.fpsmaster.replay.adapter.ReplayWorldAdapter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns a live connection into a `.novareplay` file.
 *
 * Capture runs on the network thread, at the point a packet has been decoded and not yet handled.
 * All that happens there is encoding the packet to bytes and offering it to a bounded queue; a
 * writer thread compresses and writes. Nothing on the network thread waits for the disk, so a slow
 * disk cannot become connection lag.
 *
 * When the queue fills, recording *stops* rather than dropping records. A recording with holes in it
 * looks complete and plays back wrong; one that ends early says so.
 *
 * A session that starts mid-game opens on nothing — a server never re-sends what the client already
 * has — so the packets that describe the session are kept as they go past ([bootstrap]) and written
 * ahead of everything else, together with a snapshot of the loaded chunks.
 */
object ReplayRecorder {

    /** How often the local player's own position is sampled, in Hz. */
    const val POSITION_SAMPLE_HZ: Int = 20

    private const val EQUIPMENT_INTERVAL_MILLIS = 500L

    @Volatile
    private var queue: ReplayWriteQueue? = null

    @Volatile
    var file: File? = null
        private set

    private var startMillis = 0L
    private var lastEquipment = 0L

    /**
     * Session packets seen since the last login, kept encoded so a recording started later can open
     * with them. Bounded: the allowlist is small and each entry replaces the last of its kind.
     */
    private val bootstrap = LinkedHashMap<String, ByteArray>()

    val isRecording: Boolean
        get() = queue?.isAccepting == true

    val recordsWritten: Int
        get() = queue?.recordsAccepted ?: 0

    val elapsedMillis: Long
        get() = if (startMillis == 0L) 0L else System.currentTimeMillis() - startMillis

    /**
     * Network thread. Called once per decoded clientbound packet, before it is handled.
     *
     * Bundles are taken apart here rather than stored whole: the protocol codec has no entry for a
     * bundle, and consecutive records replay in the same tick anyway.
     */
    fun onClientboundPacket(packet: Packet<*>) {
        val registries = ReplayWorldAdapter.liveRegistries()
        if (ReplayWorldAdapter.isSessionStart(packet)) {
            bootstrap.clear()
        }
        for (single in ReplayPacketAdapter.expand(packet)) {
            val payload = ReplayPacketAdapter.encode(single, registries) ?: continue
            if (ReplayWorldAdapter.isBootstrapPacket(single)) {
                bootstrap[single.javaClass.simpleName] = payload
            }
            val active = queue ?: continue
            if (!active.offer(ReplayRecord.packet(millisNow(), payload))) {
                reportStop(active)
            }
        }
    }

    /** Client thread, once per tick: the local player's own state, which no packet carries. */
    fun onClientTick() {
        val active = queue ?: return
        if (!active.isAccepting) {
            reportStop(active)
            return
        }
        val millis = millisNow()
        ReplayWorldAdapter.sampleLocalPlayer(millis)?.let { active.offer(it) }
        val now = System.currentTimeMillis()
        if (now - lastEquipment >= EQUIPMENT_INTERVAL_MILLIS) {
            lastEquipment = now
            captureEquipment(millis, active)
        }
    }

    /** Client thread. Returns the file being written, or null with the reason already logged. */
    fun start(directory: File): File? {
        if (isRecording) {
            return file
        }
        val profile = ReplayWorldAdapter.localProfile()
        if (profile == null) {
            logger.warn("[replay] not in a world")
            return null
        }
        if (!directory.isDirectory && !directory.mkdirs()) {
            logger.warn("[replay] could not create $directory")
            return null
        }
        val target = File(directory, "${NAME_FORMAT.format(Date())}$EXTENSION")
        startMillis = System.currentTimeMillis()
        lastEquipment = 0L
        val recordingProfile = RecordingProfile(
            recorderName = ReplayWorldAdapter.localName(),
            recorderId = ReplayWorldAdapter.localId(),
            dimension = ReplayWorldAdapter.currentDimension(),
            serverAddress = ReplayWorldAdapter.currentServerAddress(),
            positionSampleHz = POSITION_SAMPLE_HZ,
            capturesEquipment = true,
        )
        val writer = try {
            NovaReplayFile.openForWrite(
                target,
                ReplayPacketAdapter.minecraftVersion,
                startMillis,
                recordingProfile,
            )
        } catch (failure: Exception) {
            logger.warn("[replay] could not open $target: ${failure.message}")
            return null
        }
        val active = ReplayWriteQueue(writer)
        queue = active
        file = target
        active.startWriter("nova-replay-writer")
        writeOpening(active)
        logger.info("[replay] recording to ${target.name}")
        return target
    }

    /** Client thread. Drains what is queued, closes the file and returns it. */
    fun stop(): File? {
        val active = queue ?: return null
        queue = null
        active.finish()
        val written = active.recordsAccepted
        val target = file
        logger.info(
            "[replay] stopped after $written records" +
                (active.stopReason?.let { " ($it)" } ?: "")
        )
        return target
    }

    /**
     * The session state and terrain the replay needs to open, written before anything live.
     *
     * These carry the timestamp 0 rather than the moment they were captured, so seeking to the start
     * of a recording applies them first however far the timeline is scrubbed.
     */
    private fun writeOpening(active: ReplayWriteQueue) {
        bootstrap.values.forEach { payload ->
            active.offer(ReplayRecord.snapshotPacket(0, payload))
        }
        val registries = ReplayWorldAdapter.liveRegistries()
        ReplayWorldAdapter.snapshotChunks().forEach { packet ->
            val payload = ReplayPacketAdapter.encode(packet, registries) ?: return@forEach
            if (!active.offer(ReplayRecord.snapshotPacket(0, payload))) {
                reportStop(active)
                return
            }
        }
    }

    private fun captureEquipment(millis: Int, active: ReplayWriteQueue) {
        val registries = ReplayWorldAdapter.liveRegistries()
        for ((slot, packet) in ReplayWorldAdapter.localEquipment()) {
            val payload = ReplayPacketAdapter.encode(packet, registries) ?: continue
            active.offer(ReplayRecord.equipment(millis, slot, payload))
        }
    }

    /** Milliseconds since the recording began, saturating rather than wrapping after 24 days. */
    private fun millisNow(): Int {
        val elapsed = System.currentTimeMillis() - startMillis
        return if (elapsed >= Int.MAX_VALUE) Int.MAX_VALUE else elapsed.toInt()
    }

    private fun reportStop(active: ReplayWriteQueue) {
        if (queue !== active) {
            return
        }
        queue = null
        val reason = active.stopReason
        if (reason == ReplayWriteQueue.StopReason.QUEUE_FULL) {
            logger.warn(
                "[replay] the writer fell ${ReplayWriteQueue.CAPACITY} records behind;" +
                    " recording stopped so the file ends where it is still correct"
            )
        }
        active.finish()
    }

    const val EXTENSION: String = ".novareplay"

    private val NAME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
}
