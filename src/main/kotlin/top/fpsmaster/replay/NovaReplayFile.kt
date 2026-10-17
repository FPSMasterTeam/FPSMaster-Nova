package top.fpsmaster.replay

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/** The file is not a NOVAREPL container, or its layout is one this build cannot read. */
class ReplayFormatException(message: String) : IOException(message)

/**
 * The recording was made on a different Minecraft version. Payloads are that version's native
 * packet encoding, so there is nothing to decode them with here — refusing is the only correct
 * answer, and it has its own type so callers can say so instead of reporting a corrupt file.
 */
class ReplayVersionMismatchException(
    val recordedVersion: String,
    val currentVersion: String,
) : IOException(
    "replay was recorded on Minecraft $recordedVersion and can only be played on $recordedVersion" +
        " (this client is $currentVersion)"
)

/**
 * What a scan of a whole recording found, without keeping any of it: how long it runs and how many
 * records it holds. Built off the client thread ([NovaReplayFile.scanIndex]) when a replay opens.
 */
data class ReplayIndex(val durationMillis: Int, val recordCount: Long, val blockCount: Int)

/**
 * On-disk format for a recorded clientbound packet stream.
 *
 * Format 2 (written by this build):
 * ```
 *   header    UTF "NOVAREPL" | int 2 | UTF minecraftVersion | long wall-clock start
 *             recording profile (see RecordingProfile)                      -- uncompressed
 *   block     int "NRBK" | int minMillis | int maxMillis | int recordCount
 *             int rawLength | int storedLength | int crc32(stored) | stored  -- zlib
 * ```
 * Format 1 (read only): the same header and records, the whole file one gzip stream.
 *
 * Inside a block, and in format 1, records are:
 * ```
 *   record    byte type | int millis-since-start | ...
 *     type 0  int payload length | payload bytes
 *     type 1  double x,y,z | float yaw,pitch | byte flags
 *     type 2  byte slot | int payload length | payload bytes
 *     type 3  int payload length | payload bytes
 * ```
 *
 * Blocks exist so a recording can be indexed without inflating it: [scanIndex] reads block headers
 * and skips the bodies, so the duration of an hour-long file is known in milliseconds. A block never
 * splits a record. Each flush closes a block, so a crash still costs only what came after the last
 * flush, and a torn last block fails its length or CRC check and reads as the end of the recording.
 *
 * A packet payload is whatever [ReplayPacketAdapter][top.fpsmaster.replay.adapter.ReplayPacketAdapter]
 * produced on the recording client: the packet id and body as that Minecraft version's own codec
 * writes them. Nothing here interprets it, which is why the header's `minecraftVersion` is a hard
 * gate on [openForRead] rather than advice.
 *
 * Type 1 and 2 are the recording client's own player. A server never sends you your own movement or
 * equipment, so replaying a session with the recorder visible in it means capturing those
 * separately. Type 3 is the world snapshot taken when recording starts mid-session: chunks and
 * entities already loaded are never re-sent, so without it the replay opens on empty space.
 */
object NovaReplayFile {

    const val MAGIC: String = "NOVAREPL"

    /** The layout this build writes. */
    const val FORMAT_VERSION: Int = 2

    /** The original single-gzip-stream layout. Still read; never written. */
    const val LEGACY_FORMAT_VERSION: Int = 1

    const val TYPE_PACKET: Int = 0
    const val TYPE_LOCAL_PLAYER: Int = 1
    const val TYPE_LOCAL_EQUIPMENT: Int = 2
    const val TYPE_SNAPSHOT_PACKET: Int = 3

    const val FLAG_ON_GROUND: Int = 1
    const val FLAG_SNEAKING: Int = 2
    const val FLAG_SPRINTING: Int = 4
    const val FLAG_SWINGING: Int = 8

    /** Sanity bound on one record. Comfortably above a full chunk packet, far below a bad length. */
    private const val MAX_PAYLOAD = 8 shl 20

    /** A block is closed once its records reach this many bytes uncompressed. */
    internal const val BLOCK_TARGET_BYTES = 256 shl 10

    /** One block holds at most a target's worth plus one maximal record; anything larger is damage. */
    private const val MAX_BLOCK_BYTES = BLOCK_TARGET_BYTES + MAX_PAYLOAD + 64

    private const val BLOCK_TAG = 0x4E52424B // "NRBK"
    private const val BLOCK_HEADER_BYTES = 7 * 4

    fun openForWrite(
        file: File,
        minecraftVersion: String,
        startMillis: Long,
        profile: RecordingProfile,
    ): Writer {
        val out = DataOutputStream(BufferedOutputStream(FileOutputStream(file), 1 shl 16))
        try {
            out.writeUTF(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeUTF(minecraftVersion)
            out.writeLong(startMillis)
            profile.write(out)
            out.flush()
        } catch (failure: IOException) {
            out.close()
            throw failure
        }
        return Writer(out)
    }

    /** Buffers records into a block and writes the block, compressed, when it fills or is flushed. */
    class Writer internal constructor(private val out: DataOutputStream) : Closeable {
        private val raw = ExposedBuffer(BLOCK_TARGET_BYTES + (BLOCK_TARGET_BYTES shr 2))
        private val records = DataOutputStream(raw)
        private val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        private val crc = CRC32()
        private var stored = ByteArray(BLOCK_TARGET_BYTES shr 1)
        private var count = 0
        private var minMillis = 0
        private var maxMillis = 0

        fun write(record: ReplayRecord) {
            writeRecord(records, record)
            if (count == 0) {
                minMillis = record.millis
                maxMillis = record.millis
            } else {
                minMillis = minOf(minMillis, record.millis)
                maxMillis = maxOf(maxMillis, record.millis)
            }
            count++
            if (raw.size() >= BLOCK_TARGET_BYTES) {
                writeBlock()
            }
        }

        /** Closes the current block and pushes it to the file, so everything so far is readable. */
        fun flush() {
            writeBlock()
            out.flush()
        }

        override fun close() {
            try {
                writeBlock()
            } finally {
                deflater.end()
                out.close()
            }
        }

        private fun writeBlock() {
            if (count == 0) {
                return
            }
            deflater.reset()
            deflater.setInput(raw.array(), 0, raw.size())
            deflater.finish()
            var length = 0
            while (!deflater.finished()) {
                if (length == stored.size) {
                    stored = stored.copyOf(stored.size * 2)
                }
                length += deflater.deflate(stored, length, stored.size - length)
            }
            crc.reset()
            crc.update(stored, 0, length)
            out.writeInt(BLOCK_TAG)
            out.writeInt(minMillis)
            out.writeInt(maxMillis)
            out.writeInt(count)
            out.writeInt(raw.size())
            out.writeInt(length)
            out.writeInt(crc.value.toInt())
            out.write(stored, 0, length)
            raw.reset()
            count = 0
        }
    }

    /**
     * Opens a recording for playback, refusing anything this client cannot decode. Reads either
     * format; records come back the same way from both.
     *
     * @throws ReplayFormatException the file is not a NOVAREPL container of a known layout
     * @throws ReplayVersionMismatchException it is, but from another Minecraft version
     */
    fun openForRead(file: File, currentMinecraftVersion: String): Reader {
        val opened = openContainer(file)
        if (opened.header.minecraftVersion != currentMinecraftVersion) {
            opened.records.close()
            throw ReplayVersionMismatchException(opened.header.minecraftVersion, currentMinecraftVersion)
        }
        return Reader(opened.records, opened.header)
    }

    /** Header only, for listing recordings without committing to playing them. */
    fun readHeader(file: File): Header = openContainer(file).let { opened ->
        opened.records.close()
        opened.header
    }

    /**
     * Walks the whole recording for its length. Format 2 reads block headers only; format 1 has no
     * blocks, so it is inflated record by record — which is why this runs off the client thread.
     * Stops early, with what it has, once [cancelled] says so.
     */
    fun scanIndex(file: File, cancelled: () -> Boolean = { false }): ReplayIndex {
        if (isLegacy(file)) {
            return openContainer(file).let { opened ->
                Reader(opened.records, opened.header).use { reader ->
                    var duration = 0
                    var count = 0L
                    while (!cancelled()) {
                        val record = reader.read() ?: break
                        duration = maxOf(duration, record.millis)
                        count++
                    }
                    ReplayIndex(duration, count, 0)
                }
            }
        }
        RandomAccessFile(file, "r").use { input ->
            readHeader(input, FORMAT_VERSION)
            var duration = 0
            var count = 0L
            var blocks = 0
            val length = input.length()
            while (!cancelled() && length - input.filePointer >= BLOCK_HEADER_BYTES) {
                if (input.readInt() != BLOCK_TAG) {
                    break
                }
                input.readInt() // minMillis
                val maxMillis = input.readInt()
                val records = input.readInt()
                input.readInt() // rawLength
                val storedLength = input.readInt()
                input.readInt() // crc
                if (storedLength < 0 || input.filePointer + storedLength > length) {
                    break // torn last block: the stream reader stops here as well
                }
                input.seek(input.filePointer + storedLength)
                duration = maxOf(duration, maxMillis)
                count += records
                blocks++
            }
            return ReplayIndex(duration, count, blocks)
        }
    }

    data class Header(
        val minecraftVersion: String,
        val startMillis: Long,
        val profile: RecordingProfile,
    )

    class Reader internal constructor(private val input: DataInputStream, val header: Header) : Closeable {
        /**
         * The next record, or null once no complete one remains.
         *
         * A truncated tail is the end, not an error: a recording cut short by a crash is still worth
         * everything written before it, and the session that ends that way is the hardest to capture
         * again.
         */
        fun read(): ReplayRecord? = try {
            readRecord()
        } catch (truncated: IOException) {
            null
        }

        private fun readRecord(): ReplayRecord? {
            val type = input.readByte().toInt()
            val millis = input.readInt()
            return when (type) {
                TYPE_LOCAL_PLAYER -> ReplayRecord.localPlayer(
                    millis,
                    input.readDouble(), input.readDouble(), input.readDouble(),
                    input.readFloat(), input.readFloat(), input.readByte().toInt()
                )

                TYPE_LOCAL_EQUIPMENT -> {
                    val slot = input.readByte().toInt()
                    readPayload()?.let { ReplayRecord.equipment(millis, slot, it) }
                }

                TYPE_PACKET -> readPayload()?.let { ReplayRecord.packet(millis, it) }
                TYPE_SNAPSHOT_PACKET -> readPayload()?.let { ReplayRecord.snapshotPacket(millis, it) }
                // An unknown type means the stream ended inside a record and we read a byte of it.
                else -> null
            }
        }

        private fun readPayload(): ByteArray? {
            val length = input.readInt()
            if (length < 0 || length > MAX_PAYLOAD) {
                return null
            }
            val payload = ByteArray(length)
            input.readFully(payload)
            return payload
        }

        override fun close() = input.close()
    }

    // ------------------------------------------------------------------ containers

    private class Opened(val header: Header, val records: DataInputStream)

    /** gzip's magic bytes; a format 2 file starts with the UTF length of "NOVAREPL" instead. */
    private fun startsWithGzip(input: InputStream): Boolean {
        input.mark(2)
        val first = input.read()
        val second = input.read()
        input.reset()
        return first == 0x1f && second == 0x8b
    }

    private fun isLegacy(file: File): Boolean =
        BufferedInputStream(FileInputStream(file), 2).use { startsWithGzip(it) }

    private fun openContainer(file: File): Opened {
        val buffered = BufferedInputStream(FileInputStream(file), 1 shl 16)
        try {
            if (startsWithGzip(buffered)) {
                val gzip = DataInputStream(GZIPInputStream(buffered, 1 shl 16))
                return Opened(readHeader(file, gzip, LEGACY_FORMAT_VERSION), gzip)
            }
            val plain = DataInputStream(buffered)
            val header = readHeader(file, plain, FORMAT_VERSION)
            return Opened(header, DataInputStream(BlockInputStream(plain)))
        } catch (failure: IOException) {
            buffered.close()
            if (failure is ReplayFormatException) {
                throw failure
            }
            throw ReplayFormatException("${file.name} ends inside its header")
        }
    }

    private fun readHeader(file: File, input: DataInput, expectedVersion: Int): Header = try {
        readHeader(input, expectedVersion)
    } catch (failure: IOException) {
        if (failure is ReplayFormatException) throw failure
        throw ReplayFormatException("${file.name} ends inside its header")
    }

    private fun readHeader(input: DataInput, expectedVersion: Int): Header {
        val magic = try {
            input.readUTF()
        } catch (malformed: IOException) {
            throw ReplayFormatException("not a Nova replay: unreadable magic")
        }
        if (magic != MAGIC) {
            throw ReplayFormatException("not a Nova replay: magic was \"$magic\"")
        }
        val formatVersion = input.readInt()
        if (formatVersion != expectedVersion) {
            throw ReplayFormatException(
                "replay format version $formatVersion in a format $expectedVersion container;" +
                    " this build reads $LEGACY_FORMAT_VERSION and $FORMAT_VERSION"
            )
        }
        return Header(input.readUTF(), input.readLong(), RecordingProfile.read(input))
    }

    internal fun writeRecord(out: DataOutputStream, record: ReplayRecord) {
        out.writeByte(record.type)
        out.writeInt(record.millis)
        when (record.type) {
            TYPE_LOCAL_PLAYER -> {
                out.writeDouble(record.x)
                out.writeDouble(record.y)
                out.writeDouble(record.z)
                out.writeFloat(record.yaw)
                out.writeFloat(record.pitch)
                out.writeByte(record.flags)
            }

            TYPE_LOCAL_EQUIPMENT -> {
                out.writeByte(record.slot)
                writePayload(out, record.payload)
            }

            else -> writePayload(out, record.payload)
        }
    }

    private fun writePayload(out: DataOutputStream, payload: ByteArray?) {
        val bytes = payload ?: ByteArray(0)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private class ExposedBuffer(size: Int) : ByteArrayOutputStream(size) {
        fun array(): ByteArray = buf
    }

    /**
     * The records of every block, inflated one block at a time and read as one stream. A block that
     * is torn, fails its CRC or does not inflate to its stated length ends the stream there.
     */
    private class BlockInputStream(private val source: DataInputStream) : InputStream() {
        private val inflater = Inflater()
        private val crc = CRC32()
        private var block = ByteArray(0)
        private var length = 0
        private var position = 0
        private var ended = false

        override fun read(): Int {
            if (position >= length && !nextBlock()) {
                return -1
            }
            return block[position++].toInt() and 0xff
        }

        override fun read(target: ByteArray, offset: Int, count: Int): Int {
            if (count == 0) {
                return 0
            }
            if (position >= length && !nextBlock()) {
                return -1
            }
            val n = minOf(count, length - position)
            System.arraycopy(block, position, target, offset, n)
            position += n
            return n
        }

        private fun nextBlock(): Boolean {
            while (!ended) {
                val loaded = try {
                    loadBlock()
                } catch (end: EOFException) {
                    false
                } catch (damaged: DataFormatException) {
                    false
                }
                if (!loaded) {
                    ended = true
                    return false
                }
                if (length > 0) {
                    return true
                }
            }
            return false
        }

        private fun loadBlock(): Boolean {
            if (source.readInt() != BLOCK_TAG) {
                return false
            }
            source.readInt() // minMillis
            source.readInt() // maxMillis
            source.readInt() // recordCount
            val rawLength = source.readInt()
            val storedLength = source.readInt()
            val expectedCrc = source.readInt()
            if (rawLength < 0 || rawLength > MAX_BLOCK_BYTES || storedLength < 0 || storedLength > MAX_BLOCK_BYTES) {
                return false
            }
            val stored = ByteArray(storedLength)
            source.readFully(stored)
            crc.reset()
            crc.update(stored, 0, storedLength)
            if (crc.value.toInt() != expectedCrc) {
                return false
            }
            if (block.size < rawLength) {
                block = ByteArray(rawLength)
            }
            inflater.reset()
            inflater.setInput(stored)
            var inflated = 0
            while (inflated < rawLength && !inflater.finished()) {
                val n = inflater.inflate(block, inflated, rawLength - inflated)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break
                }
                inflated += n
            }
            if (inflated != rawLength) {
                return false
            }
            length = rawLength
            position = 0
            return true
        }

        override fun close() {
            inflater.end()
            source.close()
        }
    }
}
