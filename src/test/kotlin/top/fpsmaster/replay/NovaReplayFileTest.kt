package top.fpsmaster.replay

import java.io.File
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NovaReplayFileTest {

    private val profile = RecordingProfile(
        recorderName = "Nova",
        recorderId = UUID(1L, 2L),
        dimension = "minecraft:overworld",
        serverAddress = "play.example.net",
        positionSampleHz = 20,
        capturesEquipment = true,
    )

    private fun temporary(): File = File.createTempFile("nova-replay", ".novareplay").also { it.deleteOnExit() }

    @Test
    fun `header and records survive a round trip`() {
        val file = temporary()
        NovaReplayFile.openForWrite(file, "1.21.11", 1_700_000_000_000L, profile).use { writer ->
            writer.write(ReplayRecord.snapshotPacket(0, byteArrayOf(1, 2, 3)))
            writer.write(ReplayRecord.packet(120, byteArrayOf(9, 8, 7, 6)))
            writer.write(ReplayRecord.localPlayer(240, 1.5, 64.0, -2.5, 90f, -12f, 5))
            writer.write(ReplayRecord.equipment(360, 2, byteArrayOf(4, 4)))
        }

        val reader = NovaReplayFile.openForRead(file, "1.21.11")
        reader.use {
            assertEquals("1.21.11", it.header.minecraftVersion)
            assertEquals(1_700_000_000_000L, it.header.startMillis)
            assertEquals(profile, it.header.profile)

            val snapshot = assertNotNull(it.read())
            assertEquals(NovaReplayFile.TYPE_SNAPSHOT_PACKET, snapshot.type)
            assertContentEquals(byteArrayOf(1, 2, 3), snapshot.payload)

            val packet = assertNotNull(it.read())
            assertEquals(120, packet.millis)
            assertContentEquals(byteArrayOf(9, 8, 7, 6), packet.payload)

            val pose = assertNotNull(it.read())
            assertEquals(NovaReplayFile.TYPE_LOCAL_PLAYER, pose.type)
            assertEquals(1.5, pose.x)
            assertEquals(64.0, pose.y)
            assertEquals(-2.5, pose.z)
            assertEquals(90f, pose.yaw)
            assertEquals(-12f, pose.pitch)
            assertEquals(5, pose.flags)

            val equipment = assertNotNull(it.read())
            assertEquals(NovaReplayFile.TYPE_LOCAL_EQUIPMENT, equipment.type)
            assertEquals(2, equipment.slot)

            assertNull(it.read())
        }
    }

    @Test
    fun `a recording from another version is refused by name`() {
        val file = temporary()
        NovaReplayFile.openForWrite(file, "1.20.1", 0L, profile).use {
            it.write(ReplayRecord.packet(0, byteArrayOf(1)))
        }

        val refusal = assertFailsWith<ReplayVersionMismatchException> {
            NovaReplayFile.openForRead(file, "1.21.11")
        }
        assertEquals("1.20.1", refusal.recordedVersion)
        assertEquals("1.21.11", refusal.currentVersion)
        assertTrue(refusal.message!!.contains("1.20.1"))
        assertTrue(refusal.message!!.contains("1.21.11"))
    }

    @Test
    fun `the header can be read without opening for playback`() {
        val file = temporary()
        NovaReplayFile.openForWrite(file, "1.20.1", 42L, profile).use {
            it.write(ReplayRecord.packet(0, byteArrayOf(1)))
        }

        val header = NovaReplayFile.readHeader(file)
        assertEquals("1.20.1", header.minecraftVersion)
        assertEquals("play.example.net", header.profile.serverAddress)
    }

    @Test
    fun `a file that is not a recording is refused rather than decoded`() {
        val file = temporary()
        GZIPOutputStream(file.outputStream()).use { it.write("not a replay at all".toByteArray()) }

        assertFailsWith<ReplayFormatException> { NovaReplayFile.openForRead(file, "1.21.11") }
    }

    @Test
    fun `a truncated recording reads up to the cut`() {
        val file = temporary()
        NovaReplayFile.openForWrite(file, "1.21.11", 0L, profile).use { writer ->
            repeat(8) {
                writer.write(ReplayRecord.packet(it * 50, ByteArray(64) { 7 }))
                // A crash costs what came after the last flush; flushes close blocks.
                if (it == 3) writer.flush()
            }
        }
        val whole = file.readBytes()
        file.writeBytes(whole.copyOf(whole.size - 40))

        val records = NovaReplayFile.openForRead(file, "1.21.11").use { reader ->
            generateSequence { reader.read() }.toList()
        }
        assertTrue(records.isNotEmpty(), "a recording cut short still holds everything before the cut")
        assertTrue(records.size < 8, "the truncated tail is not reported as a complete record")
    }

    @Test
    fun `a flush makes everything so far readable while recording continues`() {
        val file = temporary()
        val writer = NovaReplayFile.openForWrite(file, "1.21.11", 0L, profile)
        writer.write(ReplayRecord.packet(10, byteArrayOf(1, 2)))
        writer.write(ReplayRecord.packet(20, byteArrayOf(3, 4)))
        writer.flush()

        val readBack = NovaReplayFile.openForRead(file, "1.21.11").use { reader ->
            generateSequence { reader.read() }.toList()
        }
        assertEquals(2, readBack.size)
        writer.close()
    }

    @Test
    fun `a corrupted block ends the recording at the block before it`() {
        val file = temporary()
        NovaReplayFile.openForWrite(file, "1.21.11", 0L, profile).use { writer ->
            repeat(3) { writer.write(ReplayRecord.packet(it, byteArrayOf(1, 2, 3))) }
            writer.flush()
            repeat(3) { writer.write(ReplayRecord.packet(10 + it, byteArrayOf(4, 5, 6))) }
        }
        val bytes = file.readBytes()
        bytes[bytes.size - 3] = (bytes[bytes.size - 3] + 1).toByte()
        file.writeBytes(bytes)

        val records = NovaReplayFile.openForRead(file, "1.21.11").use { reader ->
            generateSequence { reader.read() }.toList()
        }
        assertEquals(listOf(0, 1, 2), records.map { it.millis })
    }

    @Test
    fun `the index reads the length without inflating and agrees with a full read`() {
        val file = temporary()
        NovaReplayFile.openForWrite(file, "1.21.11", 0L, profile).use { writer ->
            for (i in 0 until 5_000) {
                writer.write(ReplayRecord.packet(i * 10, ByteArray(200) { (i + it).toByte() }))
                if (i % 700 == 0) writer.flush()
            }
        }

        val index = NovaReplayFile.scanIndex(file)
        assertEquals(49_990, index.durationMillis)
        assertEquals(5_000L, index.recordCount)
        assertTrue(index.blockCount > 1, "a recording this size spans several blocks")
    }

    // ------------------------------------------------------------------ format 1 compatibility

    @Test
    fun `a format 1 recording still reads back record for record`() {
        val file = temporary()
        LegacyReplay.write(file, "1.21.11", 99L, profile, listOf(
            ReplayRecord.snapshotPacket(0, byteArrayOf(1, 2, 3)),
            ReplayRecord.packet(120, byteArrayOf(9, 8, 7, 6)),
            ReplayRecord.localPlayer(240, 1.5, 64.0, -2.5, 90f, -12f, 5),
            ReplayRecord.equipment(360, 2, byteArrayOf(4, 4)),
        ))

        assertEquals(99L, NovaReplayFile.readHeader(file).startMillis)
        val records = NovaReplayFile.openForRead(file, "1.21.11").use { reader ->
            assertEquals(profile, reader.header.profile)
            generateSequence { reader.read() }.toList()
        }
        assertEquals(listOf(0, 120, 240, 360), records.map { it.millis })
        assertEquals(
            listOf(
                NovaReplayFile.TYPE_SNAPSHOT_PACKET,
                NovaReplayFile.TYPE_PACKET,
                NovaReplayFile.TYPE_LOCAL_PLAYER,
                NovaReplayFile.TYPE_LOCAL_EQUIPMENT,
            ),
            records.map { it.type },
        )
        assertContentEquals(byteArrayOf(9, 8, 7, 6), records[1].payload)
        assertEquals(-12f, records[2].pitch)
        assertEquals(2, records[3].slot)

        val index = NovaReplayFile.scanIndex(file)
        assertEquals(360, index.durationMillis)
        assertEquals(4L, index.recordCount)
    }

    @Test
    fun `a format 1 recording from another version is still refused by name`() {
        val file = temporary()
        LegacyReplay.write(file, "1.20.1", 0L, profile, listOf(ReplayRecord.packet(0, byteArrayOf(1))))

        assertFailsWith<ReplayVersionMismatchException> { NovaReplayFile.openForRead(file, "1.21.11") }
    }

    @Test
    fun `new recordings are written as format 2`() {
        val file = temporary()
        NovaReplayFile.openForWrite(file, "1.21.11", 0L, profile).close()

        val input = java.io.DataInputStream(file.inputStream())
        input.use {
            assertEquals(NovaReplayFile.MAGIC, it.readUTF())
            assertEquals(NovaReplayFile.FORMAT_VERSION, it.readInt())
        }
        assertEquals(2, NovaReplayFile.FORMAT_VERSION)
    }
}
