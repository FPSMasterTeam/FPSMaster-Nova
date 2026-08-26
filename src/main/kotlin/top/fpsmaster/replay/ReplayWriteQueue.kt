package top.fpsmaster.replay

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The hand-off between packet capture and the disk.
 *
 * Records are serialised on the network thread — a memcpy — and compressed and written on a writer
 * thread. Doing either inline would add latency to packet handling and change the very timing being
 * recorded.
 *
 * The queue is bounded. When it fills, recording *stops*: [offer] never blocks and never waits for
 * space, because a lagging connection during capture is not recoverable while a truncated recording
 * is. A silent drop-and-continue would be worse than either — the file would look complete and
 * replay a session with holes in it.
 */
class ReplayWriteQueue(
    private val writer: NovaReplayFile.Writer,
    private val clock: () -> Long = System::currentTimeMillis,
    capacity: Int = CAPACITY,
    private val flushIntervalMillis: Long = FLUSH_INTERVAL_MILLIS,
) {
    companion object {
        /** Bounded so a stalled disk cannot turn into unbounded heap growth during a match. */
        const val CAPACITY: Int = 4096

        /** How much of a recording a hard crash may cost. */
        const val FLUSH_INTERVAL_MILLIS: Long = 2000L

        /** How long the writer waits for a record before checking the flush deadline again. */
        private const val POLL_MILLIS = 200L
    }

    private val queue = ArrayBlockingQueue<ReplayRecord>(capacity)

    @Volatile
    private var accepting = true

    /** Why recording ended, or null while it is still running. */
    @Volatile
    var stopReason: StopReason? = null
        private set

    @Volatile
    var recordsAccepted: Int = 0
        private set

    private var lastFlush = clock()
    private var thread: Thread? = null

    enum class StopReason { QUEUE_FULL, WRITE_FAILED, REQUESTED }

    val isAccepting: Boolean
        get() = accepting

    /**
     * Network thread. Returns false once recording has ended — the first caller to find the queue
     * full is the one that ends it.
     */
    fun offer(record: ReplayRecord): Boolean {
        if (!accepting) {
            return false
        }
        if (!queue.offer(record)) {
            stop(StopReason.QUEUE_FULL)
            return false
        }
        recordsAccepted++
        return true
    }

    /** Stops accepting records. The writer still drains what is already queued and closes the file. */
    fun stop(reason: StopReason) {
        if (accepting) {
            stopReason = reason
            accepting = false
        }
    }

    fun startWriter(threadName: String) {
        check(thread == null) { "writer already started" }
        val worker = Thread({ runWriter() }, threadName)
        worker.isDaemon = true
        thread = worker
        worker.start()
    }

    /** Ends the recording and waits for the writer to drain and close the file. */
    fun finish(joinMillis: Long = 5000L) {
        stop(StopReason.REQUESTED)
        val worker = thread
        if (worker == null) {
            drainAndClose()
            return
        }
        try {
            worker.join(joinMillis)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
    }

    private fun runWriter() {
        try {
            while (accepting || queue.isNotEmpty()) {
                pumpOnce(queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS))
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (failure: Exception) {
            stop(StopReason.WRITE_FAILED)
        } finally {
            closeQuietly()
        }
    }

    /**
     * Writes one record and flushes on the deadline. Split out so the flush schedule can be driven
     * from a test clock instead of a real one.
     */
    internal fun pumpOnce(record: ReplayRecord?) {
        if (record != null) {
            writer.write(record)
        }
        // Bound how much a crash can cost. The stream is sync-flushed, so everything up to here
        // stays readable even if the process never gets to close it.
        val now = clock()
        if (now - lastFlush >= flushIntervalMillis) {
            writer.flush()
            lastFlush = now
        }
    }

    /** Drains and closes on the calling thread. Only used when no writer thread was started. */
    internal fun drainAndClose() {
        try {
            while (true) {
                pumpOnce(queue.poll() ?: break)
            }
        } catch (failure: Exception) {
            stop(StopReason.WRITE_FAILED)
        } finally {
            closeQuietly()
        }
    }

    private fun closeQuietly() {
        try {
            writer.close()
        } catch (ignored: Exception) {
            // Nothing useful is left to do with a stream that will not close.
        }
    }
}
