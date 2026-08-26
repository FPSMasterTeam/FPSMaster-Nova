package top.fpsmaster.cosmetic

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.fpsmaster.auth.AuthService
import top.fpsmaster.auth.ItemView
import top.fpsmaster.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Cosmetic loadouts of the *other* FPSMaster players on the server, keyed by Mojang-verified
 * Minecraft UUID.
 *
 * Lookups are demand driven: a render layer asks about a UUID, and the first ask queues it for the
 * next batch resolve. Entries live [TTL_MS] and are then re-requested, which is also what picks up
 * a player switching cosmetics mid-session. A UUID the server does not know is cached as
 * [Entry.EMPTY] so an unlinked or offline-account player is asked about once per TTL instead of
 * once per frame.
 *
 * Leaving a world or logging out must call [clear]; nothing here survives a session.
 */
object CosmeticLoadoutCache {
    /** Plan §5.7: batch resolve accepts at most 200 canonical UUIDs. */
    private const val MAX_BATCH = 200
    private const val TTL_MS = 5L * 60L * 1000L
    private const val RETRY_MS = 15L * 1000L
    private const val FLUSH_INTERVAL_MS = 250L
    private const val PRODUCTION_API = "https://api.fpsmaster.top/api/v1"

    data class Loadout(
        val capeItem: ItemView?,
        val backItem: ItemView?,
        val builtinWingsEnabled: Boolean,
        val wingScale: Float,
        val capeAnimationEnabled: Boolean
    ) {
        val rendersDragonWings: Boolean
            get() = builtinWingsEnabled || backItem?.category?.lowercase() == "wings"

        val rendersElytra: Boolean
            get() = !builtinWingsEnabled && backItem?.category?.lowercase() == "elytra"
    }

    private class Entry(val loadout: Loadout?, val fetchedAt: Long) {
        fun stale(now: Long): Boolean = now - fetchedAt >= (if (loadout == null) RETRY_MS else TTL_MS)
    }

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    private val entries = ConcurrentHashMap<UUID, Entry>()
    private val queued = ConcurrentHashMap.newKeySet<UUID>()
    private val inFlight = ConcurrentHashMap.newKeySet<UUID>()

    @Volatile
    private var lastFlush = 0L

    @Volatile
    private var unavailable = false

    /**
     * Loadout for [uuid], or null when it is unknown, not published, or still being fetched.
     * Queues a refresh when the cached entry is missing or expired.
     */
    @JvmStatic
    fun get(uuid: UUID): Loadout? {
        val now = System.currentTimeMillis()
        val entry = entries[uuid]
        if (entry == null || entry.stale(now)) {
            enqueue(uuid, now)
        }
        return entry?.loadout
    }

    /** True when the resolve endpoint is missing or has been failing; UI shows "sync unavailable". */
    @JvmStatic
    fun isUnavailable(): Boolean = unavailable

    /** Drop every cached loadout. Call on world exit and on logout. */
    @JvmStatic
    fun clear() {
        entries.clear()
        queued.clear()
        inFlight.clear()
        unavailable = false
    }

    /** Pre-warm a whole tab list / player list in one batch. */
    fun request(uuids: Collection<UUID>) {
        val now = System.currentTimeMillis()
        uuids.forEach { uuid ->
            val entry = entries[uuid]
            if (entry == null || entry.stale(now)) {
                enqueue(uuid, now)
            }
        }
    }

    private fun enqueue(uuid: UUID, now: Long) {
        if (!AuthService.isLoggedIn() || uuid in inFlight) {
            return
        }
        queued.add(uuid)
        if (now - lastFlush < FLUSH_INTERVAL_MS) {
            return
        }
        lastFlush = now
        flush()
    }

    private fun flush() {
        val batch = ArrayList<UUID>(MAX_BATCH)
        val iterator = queued.iterator()
        while (iterator.hasNext() && batch.size < MAX_BATCH) {
            val uuid = iterator.next()
            iterator.remove()
            if (inFlight.add(uuid)) {
                batch.add(uuid)
            }
        }
        if (batch.isEmpty()) {
            return
        }

        val payload = JsonObject().apply {
            add("minecraftUuids", JsonArray().apply { batch.forEach { add(it.toString()) } })
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase() + "/cosmetics/loadouts/resolve"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${AuthService.accessToken.orEmpty()}")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .build()

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { response, exception ->
                try {
                    if (exception != null) {
                        unavailable = true
                        logger.warn("Cosmetic loadout resolve failed: ${exception.message}")
                        return@whenComplete
                    }
                    // 404 means this backend predates the loadout endpoint. Say so instead of
                    // silently pretending nobody wears anything.
                    if (response.statusCode() == 404 || response.statusCode() == 501) {
                        unavailable = true
                        markUnknown(batch)
                        return@whenComplete
                    }
                    if (response.statusCode() !in 200..299) {
                        unavailable = true
                        logger.warn("Cosmetic loadout resolve returned HTTP ${response.statusCode()}")
                        return@whenComplete
                    }
                    unavailable = false
                    apply(batch, response.body())
                } catch (failure: Exception) {
                    logger.warn("Failed to apply cosmetic loadouts: ${failure.message}")
                } finally {
                    batch.forEach(inFlight::remove)
                }
            }
    }

    private fun apply(batch: List<UUID>, body: String?) {
        val now = System.currentTimeMillis()
        val data = body?.takeIf { it.isNotBlank() }
            ?.let { gson.fromJson(it, JsonObject::class.java) }
            ?.get("data")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
        val resolved = HashMap<UUID, Loadout>()
        data?.forEach { element ->
            val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val uuid = parseUuid(entry.stringOrNull("minecraftUuid")) ?: return@forEach
            loadout(entry)?.let { resolved[uuid] = it }
        }
        batch.forEach { uuid -> entries[uuid] = Entry(resolved[uuid], now) }
    }

    private fun markUnknown(batch: List<UUID>) {
        val now = System.currentTimeMillis()
        batch.forEach { uuid -> entries[uuid] = Entry(null, now) }
    }

    private fun loadout(entry: JsonObject): Loadout? {
        val view = entry.get("loadout")?.takeIf { it.isJsonObject }?.asJsonObject ?: entry
        if (!view.has("builtinWingsEnabled") && !view.has("capeItem") && !view.has("backItem")) {
            return null
        }
        return Loadout(
            capeItem = view.item("capeItem"),
            backItem = view.item("backItem"),
            builtinWingsEnabled = view.get("builtinWingsEnabled")?.takeIf { it.isJsonPrimitive }
                ?.asBoolean ?: false,
            wingScale = view.get("wingScale")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 1f,
            capeAnimationEnabled = view.get("capeAnimationEnabled")?.takeIf { it.isJsonPrimitive }
                ?.asBoolean ?: false
        )
    }

    private fun JsonObject.item(name: String): ItemView? = get(name)?.takeIf { it.isJsonObject }
        ?.let { gson.fromJson(it, ItemView::class.java) }
        ?.takeIf { it.assetKey.isNotBlank() }

    private fun JsonObject.stringOrNull(name: String): String? = get(name)
        ?.takeIf { it.isJsonPrimitive }?.asString

    private fun parseUuid(value: String?): UUID? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            if (raw.length == 32) {
                UUID.fromString(
                    raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16) +
                        "-" + raw.substring(16, 20) + "-" + raw.substring(20)
                )
            } else {
                UUID.fromString(raw)
            }
        }.getOrNull()
    }

    private fun apiBase(): String {
        val configured = System.getProperty("fpsmaster.api.baseUrl")?.takeIf(String::isNotBlank)
            ?: System.getenv("FPSMASTER_API_BASE_URL")?.takeIf(String::isNotBlank)
            ?: return PRODUCTION_API
        return configured.trim().trimEnd('/') + "/api/v1"
    }
}
