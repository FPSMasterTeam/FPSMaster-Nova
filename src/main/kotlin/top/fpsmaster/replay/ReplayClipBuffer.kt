package top.fpsmaster.replay

import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.replay.adapter.ReplayPacketAdapter
import top.fpsmaster.replay.adapter.ReplayWorldAdapter
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Save the last N minutes": a rolling [ClipRing] fed from the same capture point as
 * [ReplayRecorder], written out as an ordinary `.novareplay` on demand.
 *
 * Nothing touches the disk until a save, and the save itself runs on its own thread from an
 * immutable copy of the buffer, so neither buffering nor saving can stall the render thread.
 * Keyframe terrain snapshots are taken a few chunks per tick for the same reason: snapshotting the
 * whole radius at once costs tens of milliseconds, once a minute.
 */
object ReplayClipBuffer {

    /** How often a new keyframe (and so a possible clip start) is taken. */
    private const val SEGMENT_MILLIS = 60_000

    private const val CHUNKS_PER_TICK = 12

    private const val EQUIPMENT_INTERVAL_MILLIS = 500L

    /** At most an eighth of the heap, and never more than 256 MB, however long the window. */
    private val MAX_BYTES: Long = (Runtime.getRuntime().maxMemory() / 8).coerceIn(32L shl 20, 256L shl 20)

    @Volatile
    private var ring: ClipRing? = null

    @Volatile
    private var epoch = 0L

    private var keyframeSegment: ClipRing.Segment? = null
    private val pendingChunks = ArrayDeque<kotlin.Pair<Int, Int>>()
    private var lastSegmentStart = 0
    private var lastEquipment = 0L
    private val saving = AtomicBoolean(false)

    enum class SaveResult { STARTED, NOTHING_BUFFERED, BUSY }

    val isBuffering: Boolean
        get() = ring != null

    /** Network thread. */
    fun onPacket(payload: ByteArray) {
        ring?.add(ReplayRecord.packet(now(), payload))
    }

    /** Client thread, once per tick while in a live world with buffering enabled. */
    fun tick(windowMillis: Int) {
        val active = ring ?: start()
        val now = now()
        continueKeyframe(active)
        if (keyframeSegment == null &&
            (active.segmentCount == 0 || now - lastSegmentStart >= SEGMENT_MILLIS || active.newestOverBudget)
        ) {
            beginSegment(active, now)
        }
        ReplayWorldAdapter.sampleLocalPlayer(now)?.let { active.add(it) }
        val wall = System.currentTimeMillis()
        if (wall - lastEquipment >= EQUIPMENT_INTERVAL_MILLIS) {
            lastEquipment = wall
            val registries = ReplayWorldAdapter.liveRegistries()
            for ((slot, packet) in ReplayWorldAdapter.localEquipment()) {
                val payload = ReplayPacketAdapter.encode(packet, registries) ?: continue
                active.add(ReplayRecord.equipment(now, slot, payload))
            }
        }
        active.trim(now, windowMillis)
    }

    /** Client thread. Drops everything buffered: leaving the world, or buffering switched off. */
    fun stop() {
        ring?.clear()
        ring = null
        keyframeSegment = null
        pendingChunks.clear()
    }

    /**
     * Client thread. Writes the buffered clip to [directory] on a background thread; [onDone] runs
     * on the client thread with the file, or null when writing failed.
     */
    fun save(directory: File, onDone: (File?) -> Unit): SaveResult {
        val clip = ring?.clip() ?: return SaveResult.NOTHING_BUFFERED
        if (clip.records.isEmpty()) {
            return SaveResult.NOTHING_BUFFERED
        }
        if (!saving.compareAndSet(false, true)) {
            return SaveResult.BUSY
        }
        val profile = RecordingProfile(
            recorderName = ReplayWorldAdapter.localName(),
            recorderId = ReplayWorldAdapter.localId(),
            dimension = ReplayWorldAdapter.currentDimension(),
            serverAddress = ReplayWorldAdapter.currentServerAddress(),
            positionSampleHz = ReplayRecorder.POSITION_SAMPLE_HZ,
            capturesEquipment = true,
        )
        val startMillis = epoch + clip.baseMillis
        val version = ReplayPacketAdapter.minecraftVersion
        val worker = Thread({
            val target = File(directory, "clip_${ReplayRecorder.formatName(Date())}${ReplayRecorder.EXTENSION}")
            val written = try {
                directory.mkdirs()
                NovaReplayFile.openForWrite(target, version, startMillis, profile).use { writer ->
                    clip.keyframe.forEach { writer.write(ReplayRecord.snapshotPacket(0, it)) }
                    clip.records.forEach { writer.write(ClipRing.rebase(it, clip.baseMillis)) }
                }
                logger.info("[replay] saved ${clip.durationMillis / 1000}s clip to ${target.name}")
                target
            } catch (failure: Exception) {
                logger.warn("[replay] could not save clip to $target", failure)
                target.delete()
                null
            } finally {
                saving.set(false)
            }
            mc.execute { onDone(written) }
        }, "nova-replay-clip-writer")
        worker.isDaemon = true
        worker.start()
        return SaveResult.STARTED
    }

    private fun start(): ClipRing {
        epoch = System.currentTimeMillis()
        lastSegmentStart = 0
        lastEquipment = 0L
        return ClipRing(MAX_BYTES).also { ring = it }
    }

    private fun beginSegment(active: ClipRing, now: Int) {
        keyframeSegment = active.beginSegment(now, ReplayRecorder.bootstrapPackets())
        lastSegmentStart = now
        pendingChunks.clear()
        pendingChunks.addAll(ReplayWorldAdapter.snapshotChunkPositions())
    }

    private fun continueKeyframe(active: ClipRing) {
        val segment = keyframeSegment ?: return
        val registries = ReplayWorldAdapter.liveRegistries()
        repeat(CHUNKS_PER_TICK) {
            val position = pendingChunks.removeFirstOrNull()
            if (position == null) {
                active.finishKeyframe(segment)
                keyframeSegment = null
                return
            }
            val packet = ReplayWorldAdapter.snapshotChunk(position.first, position.second) ?: return@repeat
            ReplayPacketAdapter.encode(packet, registries)?.let { active.addKeyframe(segment, it) }
        }
    }

    private fun now(): Int {
        val elapsed = System.currentTimeMillis() - epoch
        return if (elapsed >= Int.MAX_VALUE) Int.MAX_VALUE else elapsed.toInt()
    }
}
