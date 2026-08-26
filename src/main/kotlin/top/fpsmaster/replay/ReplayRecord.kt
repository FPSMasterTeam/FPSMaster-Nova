package top.fpsmaster.replay

/**
 * One recorded event: a clientbound packet, or a sample of the recording player's own state.
 *
 * One class rather than a sealed hierarchy because these are produced on the network thread at
 * packet rate and handed straight to a bounded queue; a per-type allocation shape buys nothing that
 * a discriminator field does not already give the writer.
 */
class ReplayRecord private constructor(
    @JvmField val type: Int,
    @JvmField val millis: Int,
    @JvmField val payload: ByteArray?,
    @JvmField val slot: Int,
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val z: Double,
    @JvmField val yaw: Float,
    @JvmField val pitch: Float,
    @JvmField val flags: Int,
) {
    val isPacket: Boolean
        get() = type == NovaReplayFile.TYPE_PACKET || type == NovaReplayFile.TYPE_SNAPSHOT_PACKET

    companion object {
        fun packet(millis: Int, payload: ByteArray): ReplayRecord =
            ReplayRecord(NovaReplayFile.TYPE_PACKET, millis, payload, -1, 0.0, 0.0, 0.0, 0f, 0f, 0)

        fun snapshotPacket(millis: Int, payload: ByteArray): ReplayRecord =
            ReplayRecord(NovaReplayFile.TYPE_SNAPSHOT_PACKET, millis, payload, -1, 0.0, 0.0, 0.0, 0f, 0f, 0)

        /** Slot 0 is the held item, 1..4 the armour, matching the equipment packet's numbering. */
        fun equipment(millis: Int, slot: Int, payload: ByteArray): ReplayRecord =
            ReplayRecord(NovaReplayFile.TYPE_LOCAL_EQUIPMENT, millis, payload, slot, 0.0, 0.0, 0.0, 0f, 0f, 0)

        fun localPlayer(
            millis: Int,
            x: Double,
            y: Double,
            z: Double,
            yaw: Float,
            pitch: Float,
            flags: Int,
        ): ReplayRecord =
            ReplayRecord(NovaReplayFile.TYPE_LOCAL_PLAYER, millis, null, -1, x, y, z, yaw, pitch, flags)
    }
}
