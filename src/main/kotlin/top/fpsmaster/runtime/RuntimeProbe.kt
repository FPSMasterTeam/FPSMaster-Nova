package top.fpsmaster.runtime

import com.google.gson.JsonObject
import top.fpsmaster.logger
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * Opt-in runtime probe for soak runs: frame pacing, GC/heap movement and net resource ownership.
 *
 * Enabled only by `-Dfpsmaster.runtimeProbe` or `-Dfpsmaster.smoke` (bare flag or `=true`), so a
 * normal session pays a single `enabled` read per call site and nothing else — this is deliberately
 * not a resident profiler: no sampling thread, no allocation while disabled, no history beyond the
 * current report window.
 *
 * A report is emitted once per [REPORT_INTERVAL_MS] of enabled frames and once on client shutdown,
 * as one JSON line prefixed with [PREFIX] so a run can be grepped and diffed against Edge's
 * benchmark counters. Frame samples reset per window; allocate/release counters are cumulative, so
 * the soak gate ("net zero after N cycles") reads `*Allocated - *Released`.
 */
object RuntimeProbe {

    const val PREFIX = "[runtime-probe]"
    private const val REPORT_INTERVAL_MS = 60_000L
    private const val MAX_FRAME_SAMPLES = 16_384

    @JvmField
    val enabled: Boolean = flag("fpsmaster.runtimeProbe") || flag("fpsmaster.smoke")

    private val texturesAllocated = AtomicLong()
    private val texturesReleased = AtomicLong()

    /**
     * Framebuffer ownership. Nova's only own render targets are the vanilla `PostChain` instances
     * driven from the render mixins (screen blur, 1.20.1 motion blur); everything else draws into
     * Minecraft's main target. Whoever creates or closes one of those chains reports it here.
     */
    private val framebuffersAllocated = AtomicLong()
    private val framebuffersReleased = AtomicLong()

    private val browsersOpened = AtomicLong()
    private val browsersClosed = AtomicLong()

    private val frameSamples = LongArray(if (enabled) MAX_FRAME_SAMPLES else 0)
    private var frameCount = 0
    private var framesDropped = 0L
    private var lastFrameNanos = 0L
    private var lastReportMs = 0L
    private var lastGcCount = 0L
    private var lastGcMillis = 0L

    @JvmStatic
    fun textureAllocated() {
        if (enabled) texturesAllocated.incrementAndGet()
    }

    @JvmStatic
    fun textureReleased() {
        if (enabled) texturesReleased.incrementAndGet()
    }

    @JvmStatic
    fun framebufferAllocated() {
        if (enabled) framebuffersAllocated.incrementAndGet()
    }

    @JvmStatic
    fun framebufferReleased() {
        if (enabled) framebuffersReleased.incrementAndGet()
    }

    @JvmStatic
    fun browserOpened() {
        if (enabled) browsersOpened.incrementAndGet()
    }

    @JvmStatic
    fun browserClosed() {
        if (enabled) browsersClosed.incrementAndGet()
    }

    /** Called once per rendered frame from the client tick hook. */
    @JvmStatic
    fun frame() {
        if (!enabled) return
        val now = System.nanoTime()
        val previous = lastFrameNanos
        lastFrameNanos = now
        if (previous == 0L) {
            lastReportMs = System.currentTimeMillis()
            return
        }
        if (frameCount < frameSamples.size) {
            frameSamples[frameCount++] = now - previous
        } else {
            framesDropped++
        }
        if (System.currentTimeMillis() - lastReportMs >= REPORT_INTERVAL_MS) {
            report("interval")
        }
    }

    /** Emits one report line and starts a new frame window. No-op when disabled. */
    @JvmStatic
    fun report(reason: String) {
        if (!enabled) return
        logger.info("{} {}", PREFIX, snapshot(reason))
        frameCount = 0
        framesDropped = 0
        lastReportMs = System.currentTimeMillis()
    }

    internal fun snapshot(reason: String): JsonObject {
        val json = JsonObject()
        json.addProperty("reason", reason)
        addFrames(json)
        addMemory(json)
        addThreads(json)
        addResources(json)
        return json
    }

    private fun addFrames(json: JsonObject) {
        json.addProperty("frames", frameCount.toLong() + framesDropped)
        json.addProperty("framesSampled", frameCount)
        if (frameCount == 0) return
        val sorted = frameSamples.copyOf(frameCount)
        sorted.sort()
        json.addProperty("frameP50Ms", millis(percentile(sorted, 0.50)))
        json.addProperty("frameP95Ms", millis(percentile(sorted, 0.95)))
        json.addProperty("frameP99Ms", millis(percentile(sorted, 0.99)))
        json.addProperty("frameMaxMs", millis(sorted[sorted.size - 1]))
    }

    private fun addMemory(json: JsonObject) {
        var gcCount = 0L
        var gcMillis = 0L
        ManagementFactory.getGarbageCollectorMXBeans().forEach { bean ->
            if (bean.collectionCount > 0) gcCount += bean.collectionCount
            if (bean.collectionTime > 0) gcMillis += bean.collectionTime
        }
        json.addProperty("gcCount", gcCount - lastGcCount)
        json.addProperty("gcMillis", gcMillis - lastGcMillis)
        json.addProperty("gcCountTotal", gcCount)
        json.addProperty("gcMillisTotal", gcMillis)
        lastGcCount = gcCount
        lastGcMillis = gcMillis

        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        json.addProperty("heapUsedMib", heap.used / (1024 * 1024))
        json.addProperty("heapCommittedMib", heap.committed / (1024 * 1024))
    }

    private fun addThreads(json: JsonObject) {
        var total = 0
        var own = 0
        var ownNonDaemon = 0
        // Once per report window only: cheap enough, and it is the one view that exposes both the
        // thread name and its daemon flag (the soak gate wants zero non-daemon FPSMaster workers).
        Thread.getAllStackTraces().keys.forEach { thread ->
            total++
            if (!thread.name.startsWith("fpsmaster", ignoreCase = true)) return@forEach
            own++
            if (!thread.isDaemon) ownNonDaemon++
        }
        json.addProperty("threads", total)
        json.addProperty("fpsmasterThreads", own)
        json.addProperty("fpsmasterNonDaemonThreads", ownNonDaemon)
    }

    private fun addResources(json: JsonObject) {
        val textures = texturesAllocated.get()
        val texturesFreed = texturesReleased.get()
        val framebuffers = framebuffersAllocated.get()
        val framebuffersFreed = framebuffersReleased.get()
        val browsers = browsersOpened.get()
        val browsersFreed = browsersClosed.get()
        json.addProperty("texturesAllocated", textures)
        json.addProperty("texturesReleased", texturesFreed)
        json.addProperty("texturesLive", textures - texturesFreed)
        json.addProperty("framebuffersAllocated", framebuffers)
        json.addProperty("framebuffersReleased", framebuffersFreed)
        json.addProperty("framebuffersLive", framebuffers - framebuffersFreed)
        json.addProperty("browsersOpened", browsers)
        json.addProperty("browsersClosed", browsersFreed)
        json.addProperty("browsersLive", browsers - browsersFreed)
    }

    internal fun percentile(sorted: LongArray, quantile: Double): Long {
        val index = Math.ceil(quantile * sorted.size).toInt() - 1
        return sorted[index.coerceIn(0, sorted.size - 1)]
    }

    private fun millis(nanos: Long): Double = Math.round(nanos / 1_000.0) / 1_000.0

    private fun flag(name: String): Boolean {
        val value = System.getProperty(name) ?: return false
        return value.isEmpty() || value.equals("true", ignoreCase = true)
    }
}
