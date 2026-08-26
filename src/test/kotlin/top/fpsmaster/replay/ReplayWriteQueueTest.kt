package top.fpsmaster.replay

import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplayWriteQueueTest {

    private val profile = RecordingProfile("Nova", UUID(0L, 1L), "minecraft:overworld", "local", 20, true)

    private fun writer(): Pair<File, NovaReplayFile.Writer> {
        val file = File.createTempFile("nova-queue", ".novareplay").also { it.deleteOnExit() }
        return file to NovaReplayFile.openForWrite(file, "1.21.11", 0L, profile)
    }

    @Test
    fun `a full queue stops recording instead of dropping records`() {
        val (file, writer) = writer()
        val queue = ReplayWriteQueue(writer)

        repeat(ReplayWriteQueue.CAPACITY) { index ->
            assertTrue(queue.offer(ReplayRecord.packet(index, byteArrayOf(1))), "record $index was refused")
        }
        assertEquals(ReplayWriteQueue.CAPACITY, queue.recordsAccepted)

        assertFalse(
            queue.offer(ReplayRecord.packet(9999, byteArrayOf(1))),
            "the record that finds the queue full is the one that ends the recording"
        )
        assertEquals(ReplayWriteQueue.StopReason.QUEUE_FULL, queue.stopReason)
        assertFalse(queue.isAccepting)
        assertFalse(queue.offer(ReplayRecord.packet(10000, byteArrayOf(1))))
        assertEquals(
            ReplayWriteQueue.CAPACITY,
            queue.recordsAccepted,
            "nothing is accepted once recording has stopped"
        )

        queue.finish()
        val written = NovaReplayFile.openForRead(file, "1.21.11").use { reader ->
            generateSequence { reader.read() }.count()
        }
        assertEquals(
            ReplayWriteQueue.CAPACITY,
            written,
            "everything accepted before the stop still reaches the file"
        )
    }

    @Test
    fun `the writer thread drains what is queued and closes the file`() {
        val (file, writer) = writer()
        val queue = ReplayWriteQueue(writer)
        queue.startWriter("nova-replay-test-writer")

        repeat(1000) { queue.offer(ReplayRecord.packet(it, byteArrayOf(it.toByte()))) }
        queue.finish()

        assertEquals(ReplayWriteQueue.StopReason.REQUESTED, queue.stopReason)
        val written = NovaReplayFile.openForRead(file, "1.21.11").use { reader ->
            generateSequence { reader.read() }.count()
        }
        assertEquals(1000, written)
    }

    @Test
    fun `flushing follows the clock rather than the record count`() {
        val (file, writer) = writer()
        var now = 0L
        val queue = ReplayWriteQueue(writer, clock = { now })

        queue.pumpOnce(ReplayRecord.packet(1, byteArrayOf(1)))
        assertEquals(
            0,
            readable(file),
            "nothing is flushed before the interval has passed"
        )

        now += ReplayWriteQueue.FLUSH_INTERVAL_MILLIS
        queue.pumpOnce(ReplayRecord.packet(2, byteArrayOf(2)))
        assertEquals(
            2,
            readable(file),
            "reaching the interval flushes everything written so far"
        )

        queue.finish()
    }

    private fun readable(file: File): Int = try {
        NovaReplayFile.openForRead(file, "1.21.11").use { reader ->
            generateSequence { reader.read() }.count()
        }
    } catch (nothingYet: Exception) {
        0
    }
}
