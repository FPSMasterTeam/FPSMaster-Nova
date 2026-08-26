package top.fpsmaster.replay

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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
 * On-disk format for a recorded clientbound packet stream.
 *
 * ```
 *   header    UTF "NOVAREPL" | int formatVersion | UTF minecraftVersion | long wall-clock start
 *             recording profile (see RecordingProfile)
 *   record    byte type | int millis-since-start | ...
 *     type 0  int payload length | payload bytes
 *     type 1  double x,y,z | float yaw,pitch | byte flags
 *     type 2  byte slot | int payload length | payload bytes
 *     type 3  int payload length | payload bytes
 * ```
 *
 * A packet payload is whatever [ReplayPacketAdapter] produced on the recording client: the packet
 * id and body as that Minecraft version's own codec writes them. Nothing here interprets it, which
 * is why the header's `minecraftVersion` is a hard gate on [openForRead] rather than advice.
 *
 * Type 1 and 2 are the recording client's own player. A server never sends you your own movement or
 * equipment, so replaying a session with the recorder visible in it means capturing those
 * separately. Type 3 is the world snapshot taken when recording starts mid-session: chunks and
 * entities already loaded are never re-sent, so without it the replay opens on empty space.
 *
 * Gzipped with `syncFlush`, so a periodic flush emits a complete deflate block: a crash mid-session
 * costs everything since the last flush rather than the whole file.
 */
object NovaReplayFile {

    const val MAGIC: String = "NOVAREPL"
    const val FORMAT_VERSION: Int = 1

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

    fun openForWrite(
        file: File,
        minecraftVersion: String,
        startMillis: Long,
        profile: RecordingProfile,
    ): Writer {
        val out = DataOutputStream(
            GZIPOutputStream(BufferedOutputStream(FileOutputStream(file), 1 shl 16), true)
        )
        out.writeUTF(MAGIC)
        out.writeInt(FORMAT_VERSION)
        out.writeUTF(minecraftVersion)
        out.writeLong(startMillis)
        profile.write(out)
        out.flush()
        return Writer(out)
    }

    class Writer(private val out: DataOutputStream) : Closeable {
        fun write(record: ReplayRecord) {
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
                    writePayload(record.payload)
                }

                else -> writePayload(record.payload)
            }
        }

        private fun writePayload(payload: ByteArray?) {
            val bytes = payload ?: ByteArray(0)
            out.writeInt(bytes.size)
            out.write(bytes)
        }

        fun flush() = out.flush()

        override fun close() = out.close()
    }

    /**
     * Opens a recording for playback, refusing anything this client cannot decode.
     *
     * @throws ReplayFormatException the file is not a NOVAREPL container of this layout
     * @throws ReplayVersionMismatchException it is, but from another Minecraft version
     */
    fun openForRead(file: File, currentMinecraftVersion: String): Reader {
        val input = DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(file), 1 shl 16)))
        val header = try {
            readHeader(input)
        } catch (failure: IOException) {
            input.close()
            if (failure is ReplayFormatException) {
                throw failure
            }
            throw ReplayFormatException("${file.name} ends inside its header")
        }
        if (header.minecraftVersion != currentMinecraftVersion) {
            input.close()
            throw ReplayVersionMismatchException(header.minecraftVersion, currentMinecraftVersion)
        }
        return Reader(input, header)
    }

    /** Header only, for listing recordings without committing to playing them. */
    fun readHeader(file: File): Header =
        DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(file), 1 shl 16)))
            .use { readHeader(it) }

    private fun readHeader(input: DataInputStream): Header {
        val magic = try {
            input.readUTF()
        } catch (malformed: IOException) {
            throw ReplayFormatException("not a Nova replay: unreadable magic")
        }
        if (magic != MAGIC) {
            throw ReplayFormatException("not a Nova replay: magic was \"$magic\"")
        }
        val formatVersion = input.readInt()
        if (formatVersion != FORMAT_VERSION) {
            throw ReplayFormatException(
                "replay format version $formatVersion, this build reads $FORMAT_VERSION"
            )
        }
        return Header(input.readUTF(), input.readLong(), RecordingProfile.read(input))
    }

    data class Header(
        val minecraftVersion: String,
        val startMillis: Long,
        val profile: RecordingProfile,
    )

    class Reader(private val input: DataInputStream, val header: Header) : Closeable {
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
}
