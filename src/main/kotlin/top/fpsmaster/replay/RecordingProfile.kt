package top.fpsmaster.replay

import java.io.DataInput
import java.io.DataOutput
import java.util.UUID

/**
 * Who and what a recording was made of. Written verbatim into the [NovaReplayFile] header so a file
 * describes itself without the client that produced it.
 *
 * [dimension] is the dimension's registry id as a string rather than the numeric index the older
 * protocols used: the numeric form stopped being stable at 1.16 and there is no version-independent
 * mapping back from it.
 */
data class RecordingProfile(
    val recorderName: String,
    val recorderId: UUID,
    val dimension: String,
    val serverAddress: String,
    /** Local-player position samples per second. 0 when the position track was not captured. */
    val positionSampleHz: Int,
    val capturesEquipment: Boolean,
) {
    fun write(out: DataOutput) {
        out.writeUTF(recorderName)
        out.writeLong(recorderId.mostSignificantBits)
        out.writeLong(recorderId.leastSignificantBits)
        out.writeUTF(dimension)
        out.writeUTF(serverAddress)
        out.writeInt(positionSampleHz)
        out.writeBoolean(capturesEquipment)
    }

    companion object {
        fun read(input: DataInput): RecordingProfile = RecordingProfile(
            recorderName = input.readUTF(),
            recorderId = UUID(input.readLong(), input.readLong()),
            dimension = input.readUTF(),
            serverAddress = input.readUTF(),
            positionSampleHz = input.readInt(),
            capturesEquipment = input.readBoolean(),
        )
    }
}
