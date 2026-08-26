package top.fpsmaster.statistics

import java.lang.management.ManagementFactory

/**
 * Frame, memory and pause statistics behind the performance overlay.
 *
 * A frame rate describes the good frames; what a player notices is the bad ones, and the two move
 * independently — a change can improve the median and worsen the worst hundredth at the same time.
 * So the distribution sits next to the rate: p50 for judging a change, the mean of the worst 1%
 * expressed as a rate for "how bad does it get", and a count of hitches (frames over twice the
 * median) because one 40ms frame is felt and forty 4ms frames are not.
 *
 * Cost per frame is one `nanoTime` and one array write; the aggregates are recomputed on a timer.
 */
object PerformanceMonitor {
    /** Frames kept for the distribution: about eight seconds at 500fps. */
    private const val WINDOW = 4096

    /** How often aggregates are recomputed — live enough to read, rare enough to be free. */
    private const val REFRESH_MILLIS = 250L

    /** A frame this many times the median counts as a hitch rather than a slow frame. */
    private const val HITCH_FACTOR = 2.0

    /**
     * Trace columns. A column keeps the *worst* frame of its slice, not the average: an average trace
     * is a smooth line that hides exactly the events it exists to show. Six seconds at 50ms a column.
     */
    private const val COLUMNS = 120
    private const val COLUMN_MILLIS = 50L
    private const val BYTES_PER_MB = 1024 * 1024

    private val frames = LongArray(WINDOW)
    private var written = 0
    private var count = 0
    private var lastFrameNanos = 0L

    private val trace = LongArray(COLUMNS)
    private var tracePosition = 0
    private var traceColumnStartedMillis = 0L

    private var lastRefreshMillis = 0L
    private var lastAllocatedBytes = -1L
    private var lastGcCount = 0L
    private var lastGcMillis = 0L

    var fps = 0.0
        private set
    var averageFps = 0.0
        private set
    var medianFrameMs = 0.0
        private set
    var onePercentLowFps = 0.0
        private set
    var worstFrameMs = 0.0
        private set
    var hitches = 0
        private set
    var heapUsedMb = 0L
        private set
    var heapMaxMb = 0L
        private set
    var heapFraction = 0.0
        private set

    /** Zero when the running JVM does not expose per-thread allocation. */
    var allocatedMbPerSecond = 0.0
        private set
    var gcPerSecond = 0.0
        private set
    var gcMillisPerSecond = 0.0
        private set

    val sampleCount: Int
        get() = count

    val columns: Int
        get() = COLUMNS

    private val threadAllocatedBytes = runCatching {
        val sunBean = Class.forName("com.sun.management.ThreadMXBean")
        if (!sunBean.isInstance(ManagementFactory.getThreadMXBean())) {
            null
        } else {
            sunBean.getMethod("getCurrentThreadAllocatedBytes")
        }
    }.getOrNull()

    private val threadMxBean = ManagementFactory.getThreadMXBean()

    /** One sample per presented frame, taken from the overlay's own render. */
    fun onFrame() {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            frames[written % WINDOW] = now - lastFrameNanos
            written++
            if (count < WINDOW) {
                count++
            }
        }
        lastFrameNanos = now

        val millis = System.currentTimeMillis()
        if (count > 0) {
            val frame = frames[(written - 1 + WINDOW) % WINDOW]
            if (millis - traceColumnStartedMillis >= COLUMN_MILLIS) {
                traceColumnStartedMillis = millis
                tracePosition = (tracePosition + 1) % COLUMNS
                trace[tracePosition] = frame
            } else if (frame > trace[tracePosition]) {
                trace[tracePosition] = frame
            }
        }
        if (millis - lastRefreshMillis >= REFRESH_MILLIS) {
            refresh(millis)
        }
    }

    /** Copies the trace oldest-first, in milliseconds. [out] must hold [columns] entries. */
    fun traceInto(out: FloatArray) {
        for (i in 0 until COLUMNS) {
            out[i] = trace[(tracePosition + 1 + i) % COLUMNS] / 1.0e6f
        }
    }

    fun reset() {
        written = 0
        count = 0
        lastFrameNanos = 0L
        trace.fill(0L)
    }

    private fun refresh(nowMillis: Long) {
        val elapsed = if (lastRefreshMillis == 0L) REFRESH_MILLIS else nowMillis - lastRefreshMillis
        lastRefreshMillis = nowMillis
        refreshFrames()
        refreshMemory(elapsed)
        refreshGc(elapsed)
    }

    private fun refreshFrames() {
        if (count == 0) {
            return
        }
        val sorted = frames.copyOf(count)
        var total = 0L
        for (frame in sorted) {
            total += frame
        }
        sorted.sort()

        averageFps = if (total == 0L) 0.0 else count * 1.0e9 / total
        medianFrameMs = sorted[count / 2] / 1.0e6
        worstFrameMs = sorted[count - 1] / 1.0e6
        // The latest frame, not a smoothed one: a lagging reading disagrees with what is on screen.
        val latest = frames[(written - 1 + WINDOW) % WINDOW]
        fps = if (latest <= 0L) 0.0 else 1.0e9 / latest

        // Mean of the worst hundredth as a rate, so a single freak frame cannot define it.
        val worstCount = maxOf(1, count / 100)
        var worstTotal = 0L
        for (i in count - worstCount until count) {
            worstTotal += sorted[i]
        }
        onePercentLowFps = if (worstTotal == 0L) 0.0 else worstCount * 1.0e9 / worstTotal

        val hitchThreshold = (sorted[count / 2] * HITCH_FACTOR).toLong()
        var over = 0
        var index = count - 1
        while (index >= 0 && sorted[index] > hitchThreshold) {
            over++
            index--
        }
        hitches = over
    }

    private fun refreshMemory(elapsedMillis: Long) {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val max = runtime.maxMemory()
        heapUsedMb = used / BYTES_PER_MB
        heapMaxMb = max / BYTES_PER_MB
        heapFraction = if (max <= 0L) 0.0 else used.toDouble() / max

        val method = threadAllocatedBytes ?: return
        try {
            val allocated = (method.invoke(threadMxBean) as Number).toLong()
            if (lastAllocatedBytes >= 0L && elapsedMillis > 0L) {
                allocatedMbPerSecond = (allocated - lastAllocatedBytes) * 1000.0 / elapsedMillis / BYTES_PER_MB
            }
            lastAllocatedBytes = allocated
        } catch (failure: Throwable) {
            // The bean stopped answering; stop asking rather than log once per frame.
            lastAllocatedBytes = -1L
        }
    }

    private fun refreshGc(elapsedMillis: Long) {
        var collections = 0L
        var collectionMillis = 0L
        ManagementFactory.getGarbageCollectorMXBeans().forEach { bean ->
            val beanCount = bean.collectionCount
            val beanMillis = bean.collectionTime
            if (beanCount > 0L) collections += beanCount
            if (beanMillis > 0L) collectionMillis += beanMillis
        }
        if (lastGcCount > 0L && elapsedMillis > 0L) {
            gcPerSecond = (collections - lastGcCount) * 1000.0 / elapsedMillis
            gcMillisPerSecond = (collectionMillis - lastGcMillis) * 1000.0 / elapsedMillis
        }
        lastGcCount = collections
        lastGcMillis = collectionMillis
    }
}
