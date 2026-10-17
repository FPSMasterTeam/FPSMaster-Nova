package top.fpsmaster.replay

import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Writes a format 1 recording byte for byte as the original writer did: one gzip stream holding the
 * header and every record. Spelled out here rather than calling into [NovaReplayFile], so a change
 * to the shared record encoding cannot quietly keep these tests passing.
 */
object LegacyReplay {
    fun write(file: File, minecraftVersion: String, startMillis: Long, profile: RecordingProfile, records: List<ReplayRecord>) {
        DataOutputStream(GZIPOutputStream(file.outputStream(), true)).use { out ->
            out.writeUTF("NOVAREPL")
            out.writeInt(1)
            out.writeUTF(minecraftVersion)
            out.writeLong(startMillis)
            profile.write(out)
            for (record in records) {
                out.writeByte(record.type)
                out.writeInt(record.millis)
                when (record.type) {
                    1 -> {
                        out.writeDouble(record.x)
                        out.writeDouble(record.y)
                        out.writeDouble(record.z)
                        out.writeFloat(record.yaw)
                        out.writeFloat(record.pitch)
                        out.writeByte(record.flags)
                    }
                    2 -> {
                        out.writeByte(record.slot)
                        out.writeInt(record.payload!!.size)
                        out.write(record.payload!!)
                    }
                    else -> {
                        out.writeInt(record.payload!!.size)
                        out.write(record.payload!!)
                    }
                }
            }
        }
    }
}
