package top.fpsmaster.replay

import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ReplayStreamingTest {

    private val profile = RecordingProfile("Nova", UUID(3L, 4L), "minecraft:overworld", "local", 20, true)
    private val version = "1.21.11"
    private val opened = ArrayList<AutoCloseable>()

    @AfterTest
    fun closeAll() = opened.forEach { it.close() }

    private fun temporary(): File = File.createTempFile("nova-stream", ".novareplay").also { it.deleteOnExit() }

    /** A payload that says which record it is, so the order things were applied in can be checked. */
    private fun tagged(ordinal: Int, size: Int = 8): ByteArray =
        ByteBuffer.allocate(maxOf(size, 4)).putInt(ordinal).array()

    private fun ordinalOf(record: ReplayRecord): Int = ByteBuffer.wrap(record.payload!!).int

    /**
     * Five terrain snapshot records at zero, then one packet every 10 ms up to [last], flushed every
     * so often so the file has many blocks. Returns every record in file order.
     */
    private fun synthetic(file: File, last: Int = 9_990): List<ReplayRecord> {
        val records = ArrayList<ReplayRecord>()
        repeat(5) { records.add(ReplayRecord.snapshotPacket(0, tagged(records.size))) }
        for (millis in 0..last step 10) {
            records.add(ReplayRecord.packet(millis, tagged(records.size)))
        }
        NovaReplayFile.openForWrite(file, version, 0L, profile).use { writer ->
            records.forEachIndexed { i, record ->
                writer.write(record)
                if (i % 97 == 0) writer.flush()
            }
        }
        return records
    }

    private class World {
        val applied = ArrayList<ReplayRecord>()
        var rebuilds = 0
    }

    private fun cursor(file: File, world: World, window: Long = 1L shl 20): ReplayCursor =
        ReplayCursor(
            openStream = { ReplayStream({ NovaReplayFile.openForRead(file, version) }, window).also { opened.add(it) } },
            apply = { world.applied.add(it) },
            rebuild = {
                world.applied.clear()
                world.rebuilds++
            },
        ).also { opened.add(it) }

    /** Pumps without a time limit until caught up, giving the reader thread time to fill the window. */
    private fun settle(cursor: ReplayCursor) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!cursor.pump(Long.MAX_VALUE)) {
            if (System.currentTimeMillis() > deadline) fail("never caught up to ${cursor.targetMillis}")
            Thread.sleep(1)
        }
    }

    private fun expectedUpTo(records: List<ReplayRecord>, millis: Int): List<Int> =
        records.filter { it.millis <= millis }.map { ordinalOf(it) }

    @Test
    fun `seeking forward applies exactly what lies between`() {
        val file = temporary()
        val records = synthetic(file)
        val world = World()
        val cursor = cursor(file, world)

        settle(cursor)
        assertEquals(expectedUpTo(records, 0), world.applied.map { ordinalOf(it) }, "time zero is the snapshot and first packet")

        cursor.seekTo(5_000)
        settle(cursor)
        assertEquals(expectedUpTo(records, 5_000), world.applied.map { ordinalOf(it) })
        assertEquals(0, world.rebuilds, "forwards never rebuilds")
    }

    @Test
    fun `seeking backwards rebuilds and lands on the same state as playing there directly`() {
        val file = temporary()
        val records = synthetic(file)
        val world = World()
        val cursor = cursor(file, world)

        cursor.seekTo(7_000)
        settle(cursor)
        cursor.seekTo(2_505)
        settle(cursor)

        assertEquals(1, world.rebuilds)
        assertEquals(expectedUpTo(records, 2_505), world.applied.map { ordinalOf(it) })
        assertEquals(2_505, cursor.targetMillis)

        // Already there: no second rebuild, nothing applied twice.
        cursor.seekTo(2_505)
        settle(cursor)
        assertEquals(1, world.rebuilds)
        assertEquals(expectedUpTo(records, 2_505), world.applied.map { ordinalOf(it) })
    }

    @Test
    fun `a seek part way through loading that goes backwards still rebuilds`() {
        val file = temporary()
        val records = synthetic(file)
        val world = World()
        val cursor = cursor(file, world)

        cursor.seekTo(9_000)
        Thread.sleep(200) // let the reader buffer ahead; the window holds this whole file
        // One record per pump: the deadline is always already past.
        repeat(400) { cursor.pump(0L) { 1L } }
        assertFalse(cursor.isCaughtUp)
        val reached = cursor.appliedMillis
        assertTrue(reached in 1..8_999, "loading was interrupted part way, at $reached")

        cursor.seekTo(reached - 500)
        settle(cursor)
        assertEquals(1, world.rebuilds, "the world was already past the new target")
        assertEquals(expectedUpTo(records, reached - 500), world.applied.map { ordinalOf(it) })
    }

    @Test
    fun `running off the end reports the end`() {
        val file = temporary()
        val records = synthetic(file, last = 990)
        val world = World()
        val cursor = cursor(file, world)

        cursor.seekTo(60_000)
        settle(cursor)
        assertTrue(cursor.isAtEnd)
        assertEquals(records.size, world.applied.size)
    }

    @Test
    fun `a budget of nothing still makes progress one record at a time`() {
        val file = temporary()
        synthetic(file)
        val world = World()
        val cursor = cursor(file, world)
        cursor.seekTo(1_000)

        val deadline = System.currentTimeMillis() + 10_000
        while (world.applied.isEmpty() && System.currentTimeMillis() < deadline) {
            cursor.pump(0L) { 1L }
            Thread.sleep(1)
        }
        assertEquals(1, world.applied.size, "exactly one record per exhausted budget")
    }

    @Test
    fun `memory holds a bounded window however long the recording is`() {
        val file = temporary()
        val payload = 64 shl 10
        val count = 3_000 // ~190 MB of records, compressing to a few MB on disk
        NovaReplayFile.openForWrite(file, version, 0L, profile).use { writer ->
            for (i in 0 until count) {
                writer.write(ReplayRecord.packet(i * 50, tagged(i, payload)))
                if (i % 40 == 0) writer.flush()
            }
        }
        val window = 2L shl 20
        val world = World()
        var appliedBytes = 0L
        var lastOrdinal = -1
        var stream: ReplayStream? = null
        val cursor = ReplayCursor(
            openStream = {
                ReplayStream({ NovaReplayFile.openForRead(file, version) }, window).also {
                    stream = it
                    opened.add(it)
                }
            },
            apply = {
                // Dropped as soon as it is applied, the way playback does: only the window is held.
                appliedBytes += it.payload!!.size
                val ordinal = ordinalOf(it)
                assertEquals(lastOrdinal + 1, ordinal, "records arrive in order")
                lastOrdinal = ordinal
            },
            rebuild = { world.rebuilds++ },
        ).also { opened.add(it) }

        // A slow consumer first, so the reader fills the window and has to wait.
        cursor.seekTo(count * 50)
        Thread.sleep(300)
        settle(cursor)

        assertEquals(count - 1, lastOrdinal)
        assertTrue(appliedBytes > window * 50, "the recording ($appliedBytes bytes) dwarfs the window")
        val peak = stream!!.peakBufferedBytes
        assertTrue(peak <= window, "read-ahead peaked at $peak bytes, over the $window byte window")
        assertTrue(peak > window / 2, "the reader did read ahead into the window ($peak bytes)")
    }

    @Test
    fun `a format 1 recording streams and seeks like a new one`() {
        val file = temporary()
        val records = (0 until 300).map { ReplayRecord.packet(it * 20, tagged(it)) }
        LegacyReplay.write(file, version, 0L, profile, records)
        val world = World()
        val cursor = cursor(file, world)

        cursor.seekTo(4_000)
        settle(cursor)
        cursor.seekTo(1_000)
        settle(cursor)

        assertEquals(1, world.rebuilds)
        assertEquals(expectedUpTo(records, 1_000), world.applied.map { ordinalOf(it) })
        assertEquals(5_980, NovaReplayFile.scanIndex(file).durationMillis)
    }
}
