package top.fpsmaster.replay

import java.io.Closeable

/**
 * Reads a recording ahead of playback on its own thread, holding at most [maxBufferedBytes] of
 * records that have been read but not yet consumed.
 *
 * This is the whole of what playback keeps in memory: records are dropped as soon as they are
 * applied, and the reader waits whenever the window is full. One record larger than the window is
 * still let through on its own, so a big chunk packet cannot wedge the stream.
 */
class ReplayStream(
    open: () -> NovaReplayFile.Reader,
    private val maxBufferedBytes: Long,
    threadName: String = "nova-replay-stream",
) : Closeable {

    private val lock = Object()
    private val buffer = ArrayDeque<ReplayRecord>()
    private var bufferedBytes = 0L

    @Volatile
    private var closed = false

    /** Set once the reader has nothing more to add: end of file, a truncated tail, or a failure. */
    @Volatile
    var isFinished: Boolean = false
        private set

    /** Why reading stopped early, if it did. A truncated tail is not a failure. */
    @Volatile
    var failure: Throwable? = null
        private set

    /** High-water mark of [bufferedBytes], for the memory bound to be checked against. */
    @Volatile
    var peakBufferedBytes: Long = 0L
        private set

    private val thread = Thread({ run(open) }, threadName).apply {
        isDaemon = true
        start()
    }

    /** The next record if one has been read, without waiting for the reader. */
    fun peek(): ReplayRecord? = synchronized(lock) { buffer.firstOrNull() }

    fun poll(): ReplayRecord? = synchronized(lock) {
        val record = buffer.removeFirstOrNull() ?: return null
        bufferedBytes -= cost(record)
        lock.notifyAll()
        record
    }

    /** True once every record has been read and consumed. */
    val isExhausted: Boolean
        get() = isFinished && synchronized(lock) { buffer.isEmpty() }

    override fun close() {
        closed = true
        synchronized(lock) {
            buffer.clear()
            bufferedBytes = 0L
            lock.notifyAll()
        }
        thread.interrupt()
    }

    private fun run(open: () -> NovaReplayFile.Reader) {
        try {
            open().use { reader ->
                while (!closed) {
                    val record = reader.read() ?: break
                    val size = cost(record)
                    synchronized(lock) {
                        while (!closed && buffer.isNotEmpty() && bufferedBytes + size > maxBufferedBytes) {
                            lock.wait()
                        }
                        if (closed) {
                            return
                        }
                        buffer.addLast(record)
                        bufferedBytes += size
                        if (bufferedBytes > peakBufferedBytes) {
                            peakBufferedBytes = bufferedBytes
                        }
                    }
                }
            }
        } catch (interrupted: InterruptedException) {
            // close(): nothing further is wanted.
        } catch (failed: Throwable) {
            failure = failed
        } finally {
            isFinished = true
        }
    }

    companion object {
        /** What one buffered record costs: its payload plus the same overhead [ClipRing] charges. */
        fun cost(record: ReplayRecord): Long = (record.payload?.size ?: 0).toLong() + ClipRing.RECORD_OVERHEAD
    }
}
