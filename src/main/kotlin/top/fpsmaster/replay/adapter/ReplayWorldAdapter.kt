package top.fpsmaster.replay.adapter

import com.mojang.authlib.GameProfile
import com.mojang.datafixers.util.Pair
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.core.RegistryAccess
import net.minecraft.network.Connection
import net.minecraft.network.PacketListener
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.world.entity.EquipmentSlot
import top.fpsmaster.mc
import top.fpsmaster.replay.NovaReplayFile
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.util.BitSet
import java.util.Optional
import java.util.UUID

//? if >=1.21 {
import net.minecraft.client.multiplayer.ClientRegistryLayer
//?}

/** Playback could not be started on this client, with the reason a user can act on. */
class ReplayPlaybackUnavailableException(message: String) : IllegalStateException(message)

/**
 * Everything playback and recording need from the running client: the live registries, the local
 * player's own state, a world snapshot, and an isolated listener to replay into.
 *
 * ## The isolated listener
 *
 * Playback builds a real [ClientPacketListener] over a [Connection] whose channel is an in-process
 * [EmbeddedChannel]. Nothing is bound, nothing is dialled, and no integrated server is started — the
 * channel exists only so the listener's own acknowledgements have somewhere to go. Recorded packets
 * are handed to that listener directly on the client thread, which is where a real connection would
 * hand them over too, so the vanilla handlers build the world exactly as they did during capture.
 *
 * The listener's constructor takes a `CommonListenerCookie` whose shape changed in four of the six
 * releases Nova targets, and most of what it carries — telemetry session, server data, post-
 * disconnect screen — is meaningless for a recording. Writing out four constructor calls would mean
 * four unverifiable branches, so the arguments are filled by type instead: the pieces a replay
 * genuinely needs are supplied by name and the rest take an empty value. A release that adds an
 * argument this cannot fill raises [ReplayPlaybackUnavailableException] naming the type rather than
 * failing somewhere deeper.
 */
object ReplayWorldAdapter {

    /** Registries the live server sent. Needed to encode packets, and null when not connected. */
    fun liveRegistries(): RegistryAccess? = mc.connection?.registryAccess()

    /** Registries to decode a recording against, in the absence of a server to send them. */
    fun playbackRegistries(): RegistryAccess {
        //? if >=1.21 {
        return ClientRegistryLayer.createRegistryAccess().compositeAccess()
        //?} else if >=1.20 {
        /*return mc.connection?.registryAccess() ?: RegistryAccess.fromRegistryOfRegistries(
            net.minecraft.core.registries.BuiltInRegistries.REGISTRY
        )
        *///?} else {
        /*return mc.connection?.registryAccess() ?: RegistryAccess.BUILTIN.get()
        *///?}
    }

    // ---------------------------------------------------------------- recording

    fun currentDimension(): String {
        val key = mc.level?.dimension() ?: return "unknown"
        //? if >=1.21.11 {
        return key.identifier().toString()
        //?} else {
        /*return key.location().toString()
        *///?}
    }

    fun currentServerAddress(): String =
        mc.currentServer?.ip ?: if (mc.hasSingleplayerServer()) "singleplayer" else "unknown"

    fun localProfile(): GameProfile? = mc.player?.gameProfile

    fun localName(): String = mc.player?.gameProfile?.name ?: "unknown"

    fun localId(): UUID = mc.player?.gameProfile?.id ?: UUID(0L, 0L)

    /** Position, rotation and pose flags of the recording client's own player. */
    fun sampleLocalPlayer(millis: Int): top.fpsmaster.replay.ReplayRecord? {
        val player = mc.player ?: return null
        var flags = 0
        //? if >=1.20 {
        if (player.onGround()) flags = flags or NovaReplayFile.FLAG_ON_GROUND
        //?} else {
        /*if (player.isOnGround) flags = flags or NovaReplayFile.FLAG_ON_GROUND
        *///?}
        if (player.isShiftKeyDown) flags = flags or NovaReplayFile.FLAG_SNEAKING
        if (player.isSprinting) flags = flags or NovaReplayFile.FLAG_SPRINTING
        if (player.swinging) flags = flags or NovaReplayFile.FLAG_SWINGING
        return top.fpsmaster.replay.ReplayRecord.localPlayer(
            millis, player.x, player.y, player.z, player.yRot, player.xRot, flags
        )
    }

    /**
     * One equipment packet per slot for the local player.
     *
     * Built as real packets so the item is written by the same codec everything else is, rather than
     * by a hand-rolled item format that would need its own version branch.
     */
    fun localEquipment(): List<kotlin.Pair<Int, Packet<*>>> {
        val player = mc.player ?: return emptyList()
        val id = player.id
        return EquipmentSlot.entries.mapNotNull { slot ->
            val stack = player.getItemBySlot(slot)
            val packet = ClientboundSetEquipmentPacket(id, listOf(Pair(slot, stack.copy())))
            slot.ordinal to (packet as Packet<*>)
        }
    }

    /**
     * The chunks already loaded when recording starts.
     *
     * A server never re-sends terrain the client already has, so a recording begun mid-session opens
     * on nothing without this. Entities are not snapshotted: their spawn packets have changed shape
     * in most of the releases Nova targets, and they reappear as the server re-sends them.
     */
    fun snapshotChunks(radius: Int = SNAPSHOT_RADIUS): List<Packet<*>> {
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()
        val lighting = level.lightEngine
        val centreX = player.blockX shr 4
        val centreZ = player.blockZ shr 4
        val packets = ArrayList<Packet<*>>()
        for (x in centreX - radius..centreX + radius) {
            for (z in centreZ - radius..centreZ + radius) {
                if (!level.chunkSource.hasChunk(x, z)) {
                    continue
                }
                val chunk = level.getChunk(x, z)
                val packet = try {
                    //? if >=1.20 {
                    ClientboundLevelChunkWithLightPacket(chunk, lighting, null as BitSet?, null as BitSet?)
                    //?} else {
                    /*ClientboundLevelChunkWithLightPacket(chunk, lighting, null as BitSet?, null as BitSet?, false)
                    *///?}
                } catch (unavailable: Throwable) {
                    null
                }
                if (packet != null) {
                    packets.add(packet)
                }
            }
        }
        return packets
    }

    /**
     * Packets that describe the session rather than a moment in it, kept aside as they arrive so a
     * recording started later can open with them.
     *
     * Matched by simple name: the classes have kept their names across every release Nova targets
     * even where their contents have not, and the bytes are replayed rather than inspected.
     */
    fun isBootstrapPacket(packet: Packet<*>): Boolean =
        packet.javaClass.simpleName in BOOTSTRAP_PACKETS

    /** Login restarts the session; anything cached before it describes a world that is gone. */
    fun isSessionStart(packet: Packet<*>): Boolean = packet.javaClass.simpleName == "ClientboundLoginPacket"

    // ---------------------------------------------------------------- playback

    /** A replay's stand-in for a server connection. Nothing here touches a socket. */
    class IsolatedSession(
        val connection: Connection,
        val listener: ClientPacketListener,
        val channel: EmbeddedChannel,
        val registries: RegistryAccess,
    ) {
        /**
         * Serverbound packets the listener produces — keep-alives, chunk-batch acknowledgements —
         * have no server to reach and would otherwise accumulate in the channel for the length of
         * the replay.
         */
        fun discardOutbound() {
            channel.outboundMessages().clear()
        }
    }

    fun open(profile: GameProfile): IsolatedSession {
        val registries = playbackRegistries()
        val connection = Connection(PacketFlow.CLIENTBOUND)
        val channel = EmbeddedChannel(connection)
        val listener = try {
            construct(ClientPacketListener::class.java, connection, profile, registries)
        } catch (failure: ReplayPlaybackUnavailableException) {
            channel.close()
            throw failure
        } catch (failure: Throwable) {
            channel.close()
            throw ReplayPlaybackUnavailableException(
                "could not open an isolated listener on this version: ${failure.javaClass.simpleName}"
            )
        }
        return IsolatedSession(connection, listener, channel, registries)
    }

    /**
     * Hands one recorded packet to the isolated listener. Must be called on the client thread — the
     * vanilla handlers assert it, and the whole point of playing back here is that they can.
     */
    @Suppress("UNCHECKED_CAST")
    fun dispatch(session: IsolatedSession, packet: Packet<*>) {
        (packet as Packet<PacketListener>).handle(session.listener)
    }

    /** Ends playback down the same path a real disconnect takes, so the client cleans up as usual. */
    fun close(session: IsolatedSession) {
        try {
            session.connection.disconnect(Component.literal("Replay ended"))
            session.connection.handleDisconnection()
        } catch (ignored: Throwable) {
            // The listener is already gone; the level teardown below is what matters.
        }
        try {
            session.channel.close()
        } catch (ignored: Throwable) {
            // Nothing left to release.
        }
        ReplayPacketAdapter.forget()
    }

    // ---------------------------------------------------------------- construction by type

    private fun <T> construct(
        type: Class<T>,
        connection: Connection,
        profile: GameProfile,
        registries: RegistryAccess,
    ): T {
        val constructor = widest(type)
            ?: throw ReplayPlaybackUnavailableException("${type.simpleName} has no usable constructor")
        val arguments = constructor.parameterTypes.map { parameter ->
            fill(parameter, connection, profile, registries)
        }
        constructor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(*arguments.toTypedArray()) as T
    }

    private fun widest(type: Class<*>): Constructor<*>? =
        type.declaredConstructors.maxByOrNull { it.parameterCount }

    private fun fill(
        parameter: Class<*>,
        connection: Connection,
        profile: GameProfile,
        registries: RegistryAccess,
    ): Any? = when {
        parameter.isAssignableFrom(mc.javaClass) -> mc
        parameter == Connection::class.java -> connection
        parameter == GameProfile::class.java -> profile
        parameter.isInstance(registries) -> registries
        parameter == Boolean::class.javaPrimitiveType -> false
        parameter == Int::class.javaPrimitiveType -> 0
        parameter == Long::class.javaPrimitiveType -> 0L
        parameter == Float::class.javaPrimitiveType -> 0f
        parameter == Double::class.javaPrimitiveType -> 0.0
        parameter == String::class.java -> REPLAY_BRAND
        parameter == Optional::class.java -> Optional.empty<Any>()
        Map::class.java.isAssignableFrom(parameter) -> LinkedHashMap<Any, Any>()
        Set::class.java.isAssignableFrom(parameter) -> LinkedHashSet<Any>()
        List::class.java.isAssignableFrom(parameter) -> ArrayList<Any>()
        else -> emptyValueOf(parameter, connection, profile, registries)
    }

    /**
     * A stand-in for a type this has no opinion about: a constant the class already publishes, then
     * its own no-argument constructor, then null.
     *
     * Null is not a failure here. The cookie is a record whose fields are only read on paths a
     * replay never takes — a telemetry session, a server to reconnect to — and vanilla's own
     * singleplayer and transfer paths pass null for several of them.
     */
    private fun emptyValueOf(
        parameter: Class<*>,
        connection: Connection,
        profile: GameProfile,
        registries: RegistryAccess,
    ): Any? {
        parameter.declaredFields
            .firstOrNull {
                Modifier.isStatic(it.modifiers) &&
                    Modifier.isPublic(it.modifiers) &&
                    parameter.isAssignableFrom(it.type) &&
                    it.name in EMPTY_CONSTANTS
            }
            ?.let { field ->
                field.isAccessible = true
                return field.get(null)
            }
        parameter.declaredConstructors
            .firstOrNull { it.parameterCount == 0 }
            ?.let { constructor ->
                return try {
                    constructor.isAccessible = true
                    constructor.newInstance()
                } catch (unavailable: Throwable) {
                    null
                }
            }
        // A nested listener cookie is the one composite worth building rather than nulling: it
        // carries the registries and profile the listener actually reads.
        if (parameter.simpleName.endsWith("Cookie")) {
            return construct(parameter, connection, profile, registries)
        }
        return null
    }

    private const val REPLAY_BRAND = "replay"

    /** How far around the recording player terrain is snapshotted, in chunks. */
    const val SNAPSHOT_RADIUS: Int = 8

    private val EMPTY_CONSTANTS = setOf("EMPTY", "DEFAULT", "VANILLA_SET", "DEFAULT_FLAGS", "NONE")

    private val BOOTSTRAP_PACKETS = setOf(
        "ClientboundLoginPacket",
        "ClientboundRespawnPacket",
        "ClientboundSetChunkCacheRadiusPacket",
        "ClientboundSetSimulationDistancePacket",
        "ClientboundSetChunkCacheCenterPacket",
        "ClientboundSetDefaultSpawnPositionPacket",
        "ClientboundPlayerAbilitiesPacket",
        "ClientboundSetHeldSlotPacket",
        "ClientboundSetCarriedItemPacket",
        "ClientboundGameEventPacket",
        "ClientboundSetTimePacket",
        "ClientboundUpdateEnabledFeaturesPacket",
        "ClientboundUpdateTagsPacket",
        "ClientboundUpdateRecipesPacket",
        "ClientboundPlayerInfoUpdatePacket",
    )
}
