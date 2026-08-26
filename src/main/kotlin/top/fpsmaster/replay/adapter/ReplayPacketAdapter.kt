package top.fpsmaster.replay.adapter

import io.netty.buffer.Unpooled
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.RegistryAccess
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow

//? if >=1.21.1 {
import io.netty.buffer.ByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.GameProtocols
//?} else {
/*import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
*///?}

//? if >=1.20 {
import net.minecraft.network.protocol.BundlePacket
//?}

/**
 * The one place that knows how a clientbound play packet turns into bytes and back.
 *
 * A recording stores each packet exactly as this Minecraft version's own codec writes it — packet id
 * and body, no Nova framing. That is why [top.fpsmaster.replay.NovaReplayFile] refuses a file from
 * another version outright: the same id means a different packet a release later, and the bytes
 * carry nothing that would let anything notice.
 *
 * Two encodings exist across the versions Nova builds for:
 *
 *  - 1.21.1 and later address packets through `GameProtocols.CLIENTBOUND_TEMPLATE`, a
 *    [StreamCodec] bound to a registry-aware buffer. Registry-backed contents (items, sounds,
 *    particles) are written as ids into the registries the server sent, so the same
 *    [RegistryAccess] must be supplied when decoding.
 *  - 1.19.2 and 1.20.1 predate the codec split: the id comes from `ConnectionProtocol.PLAY` and the
 *    packet writes itself.
 *
 * Only the 1.21.11 branch is compiled here; the others are selected by Stonecutter.
 */
object ReplayPacketAdapter {

    /**
     * The Minecraft version stamped into a recording's header and checked when one is opened.
     *
     * Read from the loader rather than `SharedConstants`, whose accessor has moved more than once
     * and would need a branch of its own.
     */
    val minecraftVersion: String by lazy {
        FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")
    }

    /**
     * Serialises a clientbound play packet, or null if this version's codec will not write it.
     *
     * A packet the codec rejects is not an error worth ending a recording over — it is a packet the
     * replay will not show. Bundles never reach here; [expand] has already taken them apart.
     */
    fun encode(packet: Packet<*>, registries: RegistryAccess?): ByteArray? = try {
        encodeUnchecked(packet, registries)
    } catch (rejected: Throwable) {
        null
    }

    /** Reverses [encode]. Null when the payload does not decode, which a truncated tail can cause. */
    fun decode(payload: ByteArray, registries: RegistryAccess?): Packet<*>? = try {
        decodeUnchecked(payload, registries)
    } catch (rejected: Throwable) {
        null
    }

    /**
     * Flattens a bundle into the packets it carries.
     *
     * The protocol codec has no entry for a bundle — the pipeline's bundler makes and unmakes it —
     * so a bundle has to be recorded as its contents. A bundle only asks that its packets be applied
     * in one tick, and consecutive records already replay together.
     */
    fun expand(packet: Packet<*>): List<Packet<*>> {
        //? if >=1.20 {
        if (packet is BundlePacket<*>) {
            val out = ArrayList<Packet<*>>()
            packet.subPackets().forEach { out.add(it) }
            return out
        }
        //?}
        return listOf(packet)
    }

    //? if >=1.21.1 {
    @Suppress("UNCHECKED_CAST")
    private fun encodeUnchecked(packet: Packet<*>, registries: RegistryAccess?): ByteArray? {
        val codec = codecFor(registries) ?: return null
        val buffer = Unpooled.buffer(INITIAL_BUFFER)
        try {
            codec.encode(buffer, packet as Packet<in ClientGamePacketListener>)
            val bytes = ByteArray(buffer.readableBytes())
            buffer.readBytes(bytes)
            return bytes
        } finally {
            buffer.release()
        }
    }

    private fun decodeUnchecked(payload: ByteArray, registries: RegistryAccess?): Packet<*>? {
        val codec = codecFor(registries) ?: return null
        val buffer = Unpooled.wrappedBuffer(payload)
        return try {
            codec.decode(buffer)
        } finally {
            buffer.release()
        }
    }

    /**
     * Binding a protocol walks its whole packet table, so the result is cached per registry set. The
     * registries change once per connection; the codec is asked for on every packet.
     */
    private var cachedRegistries: RegistryAccess? = null
    private var cachedCodec: StreamCodec<ByteBuf, Packet<in ClientGamePacketListener>>? = null

    @Synchronized
    private fun codecFor(
        registries: RegistryAccess?,
    ): StreamCodec<ByteBuf, Packet<in ClientGamePacketListener>>? {
        if (registries == null) {
            return null
        }
        val cached = cachedCodec
        if (cached != null && cachedRegistries === registries) {
            return cached
        }
        val codec = GameProtocols.CLIENTBOUND_TEMPLATE
            .bind(RegistryFriendlyByteBuf.decorator(registries))
            .codec()
        cachedRegistries = registries
        cachedCodec = codec
        return codec
    }

    /** Frees the cached protocol table when a connection ends. */
    @Synchronized
    fun forget() {
        cachedRegistries = null
        cachedCodec = null
    }
    //?} else {
    /*private fun encodeUnchecked(packet: Packet<*>, registries: RegistryAccess?): ByteArray? {
        // 1.19.2 hands back a boxed id and nulls it for an unregistered packet; 1.20.1 returns a
        // primitive. Boxing both keeps one branch honest for either.
        val boxed: Any? = ConnectionProtocol.PLAY.getPacketId(PacketFlow.CLIENTBOUND, packet)
        val id = boxed as? Int ?: return null
        if (id < 0) {
            return null
        }
        val buffer = FriendlyByteBuf(Unpooled.buffer(INITIAL_BUFFER))
        try {
            buffer.writeVarInt(id)
            packet.write(buffer)
            val bytes = ByteArray(buffer.readableBytes())
            buffer.readBytes(bytes)
            return bytes
        } finally {
            buffer.release()
        }
    }

    private fun decodeUnchecked(payload: ByteArray, registries: RegistryAccess?): Packet<*>? {
        val buffer = FriendlyByteBuf(Unpooled.wrappedBuffer(payload))
        return try {
            ConnectionProtocol.PLAY.createPacket(PacketFlow.CLIENTBOUND, buffer.readVarInt(), buffer)
        } finally {
            buffer.release()
        }
    }

    fun forget() = Unit
    *///?}

    /** Most packets are well under this; the buffer grows for the chunk ones that are not. */
    private const val INITIAL_BUFFER = 256
}
