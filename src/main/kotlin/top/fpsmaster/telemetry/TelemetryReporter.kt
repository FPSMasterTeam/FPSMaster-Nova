package top.fpsmaster.telemetry

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.fpsmaster.logger
import top.fpsmaster.mc
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object TelemetryReporter {
    private const val HEARTBEAT_INTERVAL_MS = 90_000L
    private const val PRESENCE_INTERVAL_MS = 180_000L
    private val TELEMETRY_URL = top.fpsmaster.auth.ApiBase.v1("/telemetry/heartbeat")
    private val PRESENCE_URL = top.fpsmaster.auth.ApiBase.v1("/telemetry/presence")
    private val OFFLINE_URL = top.fpsmaster.auth.ApiBase.v1("/telemetry/offline")
    private const val CLIENT_NAME = "FPSMaster-Nova"
    private const val CLIENT_KIND = "NOVA"
    private val CLIENT_VERSION = top.fpsmaster.Client.VERSION
    private const val MAX_SAMPLED_PLAYERS = 8
    private const val FIRST_BACKOFF_MS = 30_000L
    private const val MAX_BACKOFF_MS = 15 * 60_000L
    private const val SHUTDOWN_WAIT_MS = 1_500L

    // Shared with the other clients so the same account maps to the same hash across editions
    // (enables unique-user counting) without exposing raw account identity.
    private const val IDENTITY_SALT = "fpsmaster-edge-telemetry-v1"

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val heartbeatInFlight = AtomicBoolean(false)
    private val presenceInFlight = AtomicBoolean(false)

    @Volatile
    var anonymousDataEnabled = false
        private set

    @Volatile
    var telemetryInstanceId: String = UUID.randomUUID().toString()
        private set

    @Volatile
    private var multiplayerActive = false
    private var lastHeartbeatAt = 0L
    private var lastPresenceAt = 0L
    private var lastPresenceServerHash: String? = null

    /** Server address -> its hash, so the per-frame tick does not re-hash an unchanged address. */
    private var hashedServerAddress: String? = null
    private var hashedServerId: String = ""

    /**
     * After a failed send nothing goes out before this. Failures used to leave the "last sent"
     * timestamps untouched, so the next frame retried: every client hammered a struggling backend at
     * frame rate. Backoff starts at 30 s and doubles to 15 min; 429 waits the maximum.
     */
    @Volatile
    private var retryAt = 0L

    @Volatile
    private var backoffMs = 0L

    fun configure(enabled: Boolean, instanceId: String?) {
        anonymousDataEnabled = enabled
        telemetryInstanceId = instanceId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: UUID.randomUUID().toString()
        if (!enabled) {
            clearState()
        }
    }

    fun setEnabled(enabled: Boolean) {
        anonymousDataEnabled = enabled
        if (!enabled) {
            shutdown()
        }
    }

    fun tick(now: Long) {
        if (!anonymousDataEnabled) {
            clearState()
            return
        }

        if (!isRemoteMultiplayerActive()) {
            if (multiplayerActive) {
                multiplayerActive = false
                submitOffline(blocking = false)
            }
            clearState()
            return
        }

        if (now < retryAt) {
            multiplayerActive = true
            return
        }
        // Runs every frame: decide from cheap state first, build the snapshot only when it is sent.
        val serverIdHash = currentServerIdHash()
        val shouldSendHeartbeat = !multiplayerActive || now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS
        val shouldSendPresence = serverIdHash != null && (
            !multiplayerActive ||
                now - lastPresenceAt >= PRESENCE_INTERVAL_MS ||
                serverIdHash != lastPresenceServerHash
            )

        multiplayerActive = true
        if (shouldSendHeartbeat) {
            submitHeartbeat(now)
        }
        if (shouldSendPresence && !presenceInFlight.get()) {
            capturePresenceSnapshot(serverIdHash!!)?.let { submitPresence(now, it) }
        }
    }

    fun shutdown() {
        if (multiplayerActive) {
            submitOffline(blocking = true)
        }
        clearState()
    }

    private fun recordSuccess() {
        backoffMs = 0L
        retryAt = 0L
    }

    private fun recordFailure(status: Int) {
        backoffMs = if (status == 429) MAX_BACKOFF_MS
        else if (backoffMs == 0L) FIRST_BACKOFF_MS
        else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        retryAt = System.currentTimeMillis() + backoffMs
    }

    private fun submitHeartbeat(now: Long) {
        if (!heartbeatInFlight.compareAndSet(false, true)) {
            return
        }

        val payload = buildHeartbeatPayload()
        sendJsonAsync(TELEMETRY_URL, payload)
            .whenComplete { response, exception ->
                if (exception != null) {
                    recordFailure(0)
                    logger.warn("Nova heartbeat failed: ${exception.message}")
                } else if (response.statusCode() in 200..299) {
                    lastHeartbeatAt = now
                    recordSuccess()
                } else {
                    recordFailure(response.statusCode())
                    logger.warn("Nova heartbeat failed with status ${response.statusCode()}")
                }
                heartbeatInFlight.set(false)
            }
    }

    private fun submitPresence(now: Long, snapshot: PresenceSnapshot) {
        if (!presenceInFlight.compareAndSet(false, true)) {
            return
        }

        val payload = buildPresencePayload(snapshot)
        sendJsonAsync(PRESENCE_URL, payload)
            .whenComplete { response, exception ->
                if (exception != null) {
                    recordFailure(0)
                    logger.warn("Nova presence failed: ${exception.message}")
                } else if (response.statusCode() in 200..299) {
                    lastPresenceAt = now
                    lastPresenceServerHash = snapshot.serverIdHash
                    recordSuccess()
                } else {
                    recordFailure(response.statusCode())
                    logger.warn("Nova presence failed with status ${response.statusCode()}")
                }
                presenceInFlight.set(false)
            }
    }

    private fun submitOffline(blocking: Boolean) {
        val payload = buildOfflinePayload()
        if (blocking) {
            // Game shutdown: give the backend a moment, never the 10 s + 20 s HTTP timeouts.
            runCatching {
                sendJsonAsync(OFFLINE_URL, payload).get(SHUTDOWN_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            }.onFailure { exception ->
                logger.warn("Nova offline failed: ${exception.message}")
            }
            return
        }

        sendJsonAsync(OFFLINE_URL, payload)
            .exceptionally { exception ->
                logger.warn("Nova offline failed: ${exception.message}")
                null
            }
    }

    private fun buildHeartbeatPayload(): JsonObject {
        val payload = basePayload()
        val user = mc.user
        // Send only salted hashes of account identity — never the raw username or UUID.
        user.name.trim().takeIf { it.isNotEmpty() }?.let {
            payload.addProperty("usernameHash", hashIdentity(it.lowercase(Locale.ROOT)))
        }
        payload.addProperty("playerUuidHash", hashIdentity(user.profileId.toString().lowercase(Locale.ROOT)))
        return payload
    }

    private fun buildPresencePayload(snapshot: PresenceSnapshot): JsonObject {
        return basePayload().apply {
            addProperty("instanceId", telemetryInstanceId)
            addProperty("platform", System.getProperty("os.name")?.trim().orEmpty())
            addProperty("serverIpMasked", snapshot.serverIpMasked)
            addProperty("serverIdHash", snapshot.serverIdHash)
            addProperty("serverPlayerCount", snapshot.serverPlayerCount)
            addProperty("sampledPlayersPayload", snapshot.sampledPlayersPayload)
            addProperty("payloadJson", snapshot.payloadJson)
        }
    }

    private fun buildOfflinePayload(): JsonObject {
        return basePayload()
    }

    private fun basePayload(): JsonObject {
        return JsonObject().apply {
            addProperty("clientName", CLIENT_NAME)
            addProperty("clientKind", CLIENT_KIND)
            addProperty("sessionId", telemetryInstanceId)
            addProperty("clientVersion", CLIENT_VERSION)
        }
    }

    private fun isRemoteMultiplayerActive(): Boolean {
        return mc.level != null &&
            mc.player != null &&
            mc.connection?.connection?.isConnected == true &&
            !mc.hasSingleplayerServer() &&
            !mc.isLocalServer
    }

    private fun currentServerIdHash(): String? {
        val address = mc.currentServer?.ip?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (address != hashedServerAddress) {
            hashedServerAddress = address
            hashedServerId = sha256Hex(address.lowercase(Locale.ROOT))
        }
        return hashedServerId
    }

    private fun capturePresenceSnapshot(serverIdHash: String): PresenceSnapshot? {
        val rawServerAddress = hashedServerAddress ?: return null
        val playerInfos = mc.connection?.onlinePlayers ?: emptyList()
        val sampledPlayersPayload = buildSampledPlayersPayload(playerInfos, serverIdHash)
        val payloadJson = JsonObject().apply {
            addProperty("tabPlayerCount", playerInfos.size)
            mc.level?.dimension()?.toString()?.let { addProperty("dimension", it) }
        }.toString()

        return PresenceSnapshot(
            serverIpMasked = maskServerAddress(rawServerAddress),
            serverIdHash = serverIdHash,
            serverPlayerCount = playerInfos.size,
            sampledPlayersPayload = sampledPlayersPayload,
            payloadJson = payloadJson
        )
    }

    private fun buildSampledPlayersPayload(playerInfos: Collection<net.minecraft.client.multiplayer.PlayerInfo>, serverIdHash: String): String {
        val samples = JsonArray()
        val selfId = mc.player?.uuid
        playerInfos
            .asSequence()
            .mapNotNull { it.profile }
            .filter { !it.name.isNullOrBlank() }
            .take(MAX_SAMPLED_PLAYERS)
            .forEach { profile ->
                val sample = JsonObject()
                sample.addProperty("playerHash", shortenHash(sha256Hex("$serverIdHash:${profile.name.lowercase(Locale.ROOT)}"), 16))
                sample.addProperty("self", profile.id != null && profile.id == selfId)
                samples.add(sample)
            }
        return samples.toString()
    }

    private fun sendJsonAsync(url: String, payload: JsonObject): CompletableFuture<HttpResponse<String>> {
        return httpClient.sendAsync(buildRequest(url, payload), HttpResponse.BodyHandlers.ofString())
    }

    private fun buildRequest(url: String, payload: JsonObject): HttpRequest {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "$CLIENT_NAME/$CLIENT_VERSION")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .build()
    }

    private fun maskServerAddress(rawAddress: String): String {
        var host = rawAddress
        var port: String? = null
        val portSeparator = rawAddress.lastIndexOf(':')
        if (portSeparator > 0 && rawAddress.indexOf(':') == portSeparator) {
            host = rawAddress.substring(0, portSeparator)
            port = rawAddress.substring(portSeparator + 1)
        }

        val maskedHost = if (Regex("\\d+\\.\\d+\\.\\d+\\.\\d+").matches(host)) {
            val segments = host.split(".")
            "${segments.first()}.*.*.${segments.last()}"
        } else {
            host.split(".")
                .filter { it.isNotEmpty() }
                .joinToString(".") { label ->
                    if (label.length == 1) "*" else label.first() + "*".repeat(label.length - 1)
                }
        }

        return if (port.isNullOrEmpty()) maskedHost else "$maskedHost:****"
    }

    private fun hashIdentity(value: String): String = sha256Hex("$IDENTITY_SALT:$value")

    private fun sha256Hex(value: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return java.util.HexFormat.of().formatHex(hash)
    }

    private fun shortenHash(value: String, length: Int): String {
        return if (value.length <= length) value else value.substring(0, length)
    }

    private fun clearState() {
        multiplayerActive = false
        lastHeartbeatAt = 0L
        lastPresenceAt = 0L
        lastPresenceServerHash = null
    }

    private data class PresenceSnapshot(
        val serverIpMasked: String,
        val serverIdHash: String,
        val serverPlayerCount: Int,
        val sampledPlayersPayload: String,
        val payloadJson: String
    )
}
