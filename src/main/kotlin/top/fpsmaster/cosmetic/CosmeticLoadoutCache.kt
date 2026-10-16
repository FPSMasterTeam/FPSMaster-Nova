package top.fpsmaster.cosmetic

import io.github.vlouboos.standaloneevent.api.EventHandler
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import top.fpsmaster.auth.AuthService
import top.fpsmaster.auth.CosmeticLoadoutView
import top.fpsmaster.auth.FPSMasterApiClient
import top.fpsmaster.auth.ItemView
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.logger
import top.fpsmaster.mc
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Loadouts of the other players on this server, so FPSMaster cosmetics are visible between clients.
 *
 * Entries are keyed by the Mojang-verified UUID the backend resolved; a player who never linked an
 * online account simply has no entry and renders vanilla. Lookups are the render path, so they only
 * read the map — refreshing happens on the client tick.
 */
object CosmeticLoadoutCache {
    /** Also the negative-cache lifetime: a player without cosmetics is asked about again after it. */
    private const val TTL_MS = 5 * 60 * 1000L
    private const val REFRESH_INTERVAL_MS = 60 * 1000L
    private const val FIRST_BACKOFF_MS = 5_000L
    private const val MAX_BACKOFF_MS = 5 * 60 * 1000L

    data class Loadout(
        val capeItemId: String?,
        val backItemId: String?,
        val builtinWingsEnabled: Boolean,
        val wingScale: Float,
        val capeAnimationEnabled: Boolean
    )

    private class Entry(val loadout: Loadout?, val capeItem: ItemView?, val backItem: ItemView?, val fetchedAt: Long)

    private val entries = ConcurrentHashMap<UUID, Entry>()
    private val inFlight = ConcurrentHashMap.newKeySet<UUID>()

    @Volatile
    private var lastSweep = 0L

    /**
     * Failed resolves back off exponentially (5 s, 10 s, ... 5 min) instead of being retried on the
     * next tick: failures store no entries, so every online player stays "unknown" and the tick
     * would otherwise fire a new batch the moment the last one returned.
     */
    @Volatile
    private var backoffMs = 0L

    @Volatile
    private var retryAt = 0L

    @Volatile
    private var registered = false

    fun initialize() {
        if (registered) return
        registered = true
        StandaloneEventAPI.getApi().register(this)
    }

    @JvmStatic
    fun get(uuid: UUID): Loadout? = entries[uuid]?.loadout

    @JvmStatic
    fun capeTexture(uuid: UUID): TextureId? {
        val entry = entries[uuid] ?: return null
        val item = entry.capeItem ?: return null
        return CosmeticManager.textureForRemote(item)
    }

    @JvmStatic
    fun rendersDragonWings(uuid: UUID): Boolean {
        val entry = entries[uuid] ?: return false
        val loadout = entry.loadout ?: return false
        return loadout.builtinWingsEnabled || entry.backItem?.category?.lowercase(Locale.ROOT) == "wings"
    }

    @JvmStatic
    fun rendersElytra(uuid: UUID): Boolean =
        entries[uuid]?.backItem?.category?.lowercase(Locale.ROOT) == "elytra"

    @JvmStatic
    fun wingTexture(uuid: UUID): TextureId? {
        val item = entries[uuid]?.backItem ?: return null
        return CosmeticManager.textureForRemote(item)
    }

    @JvmStatic
    fun wingScale(uuid: UUID): Float = entries[uuid]?.loadout?.wingScale ?: 1f

    @JvmStatic
    fun animatesCape(uuid: UUID): Boolean = entries[uuid]?.loadout?.capeAnimationEnabled ?: false

    /** Fetch the loadouts of [uuids] that are unknown or stale. Batched to the backend's cap. */
    fun request(uuids: Collection<UUID>) {
        if (!AuthService.isLoggedIn() || uuids.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()
        val wanted = uuids.filter { uuid ->
            val entry = entries[uuid]
            (entry == null || now - entry.fetchedAt > TTL_MS) && inFlight.add(uuid)
        }
        if (wanted.isEmpty()) {
            return
        }

        wanted.chunked(FPSMasterApiClient.RESOLVE_BATCH_LIMIT).forEach { batch ->
            val keys = batch.map { it.toString().lowercase(Locale.ROOT) }
            FPSMasterApiClient.resolveLoadouts(keys).whenComplete { result, exception ->
                val fetchedAt = System.currentTimeMillis()
                if (exception != null || !result.success) {
                    // Players render vanilla meanwhile. 404 = the endpoint is not deployed, which will
                    // not change within seconds: wait the longest interval straight away.
                    backoffMs = if (result?.statusCode == 404) MAX_BACKOFF_MS
                    else if (backoffMs == 0L) FIRST_BACKOFF_MS
                    else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    retryAt = fetchedAt + backoffMs
                    logger.warn(
                        "Failed to resolve {} remote cosmetic loadouts ({}); retrying in {}s",
                        batch.size,
                        exception?.javaClass?.simpleName ?: result.code ?: "HTTP ${result.statusCode}",
                        backoffMs / 1000
                    )
                    batch.forEach(inFlight::remove)
                    return@whenComplete
                }
                backoffMs = 0L
                retryAt = 0L

                val resolved = HashMap<UUID, CosmeticLoadoutView?>()
                result.data.orEmpty().forEach { view ->
                    parseUuid(view.minecraftUuid)?.let { resolved[it] = view.loadout }
                }
                batch.forEach { uuid ->
                    entries[uuid] = entry(resolved[uuid], fetchedAt)
                    inFlight.remove(uuid)
                }
            }
        }
    }

    @JvmStatic
    fun clear() {
        entries.clear()
        inFlight.clear()
        lastSweep = 0L
        backoffMs = 0L
        retryAt = 0L
    }

    @EventHandler
    @Suppress("unused")
    fun onTick(@Suppress("UNUSED_PARAMETER") event: TickEvent) {
        val connection = mc.connection
        if (connection == null || mc.level == null) {
            if (entries.isNotEmpty()) clear()
            return
        }

        val now = System.currentTimeMillis()
        if (now < retryAt) {
            return
        }
        val online = connection.onlinePlayers.mapNotNull { it.profile?.id }
        val unknown = online.any { entries[it] == null && it !in inFlight }
        // New faces are looked up as soon as they appear; everyone else on the interval.
        if (!unknown && now - lastSweep < REFRESH_INTERVAL_MS) {
            return
        }
        lastSweep = now
        request(online)
    }

    private fun entry(view: CosmeticLoadoutView?, fetchedAt: Long): Entry {
        if (view == null) {
            return Entry(null, null, null, fetchedAt)
        }
        val loadout = Loadout(
            capeItemId = view.capeItemId?.toString(),
            backItemId = view.backItemId?.toString(),
            builtinWingsEnabled = view.builtinWingsEnabled,
            wingScale = view.wingScaleValue(),
            capeAnimationEnabled = view.capeAnimationEnabled
        )
        return Entry(loadout, view.capeItem, view.backItem, fetchedAt)
    }

    private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
