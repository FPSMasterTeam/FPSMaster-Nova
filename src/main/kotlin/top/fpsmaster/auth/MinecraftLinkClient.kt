package top.fpsmaster.auth

import com.google.gson.JsonObject
import top.fpsmaster.logger
import top.fpsmaster.mc
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Proves to the backend which Minecraft account this client is signed into, so other players can be
 * shown its cosmetics.
 *
 * The client never tells the backend its UUID. It asks for a challenge, performs a real Mojang
 * `joinServer` against the returned server id, and the backend reads the UUID back from Mojang's
 * `hasJoined`. Offline accounts cannot complete this and are simply never published — they keep their
 * own local cosmetics.
 *
 * The Minecraft session access token is used once, in-process, and is never logged or sent anywhere
 * except Mojang's session server.
 */
object MinecraftLinkClient {
    private const val JOIN_URL = "https://sessionserver.mojang.com/session/minecraft/join"
    private const val FIRST_BACKOFF_MS = 30_000L
    private const val MAX_BACKOFF_MS = 10 * 60_000L

    enum class State {
        /** Not attempted yet. */
        IDLE,

        /** Challenge in flight. */
        LINKING,

        /** Mojang-verified and stored by the backend. */
        LINKED,

        /** Offline / demo session: cannot prove ownership, cosmetics stay local. */
        OFFLINE,

        /** Attempt failed (network, expired challenge, backend rejection). */
        FAILED
    }

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var linkedUuid: UUID? = null
        private set

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    private var inFlight = false

    /** After a FAILED attempt: no new attempt before this, and how long the last wait was. */
    @Volatile
    private var retryAt = 0L

    @Volatile
    private var backoffMs = 0L

    private enum class JoinOutcome { ACCEPTED, REJECTED, UNAVAILABLE }

    /**
     * Link the current Minecraft session. Cheap enough to call every tick: a no-op while a link is in
     * flight, established, known to be impossible (offline session), or backing off after a failure
     * (30 s, doubling to 10 min).
     */
    fun ensureLinked(): CompletableFuture<Boolean> {
        if (!AuthService.isLoggedIn()) {
            return CompletableFuture.completedFuture(false)
        }
        if (inFlight || state == State.LINKED || state == State.OFFLINE ||
            (state == State.FAILED && System.currentTimeMillis() < retryAt)
        ) {
            return CompletableFuture.completedFuture(state == State.LINKED)
        }
        return link()
    }

    /** Force a fresh link attempt (used after a failure the user can retry). */
    fun link(): CompletableFuture<Boolean> {
        if (inFlight) {
            return CompletableFuture.completedFuture(false)
        }
        val session = currentSession()
        if (session == null) {
            state = State.OFFLINE
            lastError = "offline session"
            return CompletableFuture.completedFuture(false)
        }

        inFlight = true
        state = State.LINKING
        return FPSMasterApiClient.createMinecraftLinkChallenge()
            .thenCompose { result ->
                val challenge = result.data
                if (!result.success || challenge == null || challenge.serverId.isBlank()) {
                    fail(result.code ?: "HTTP ${result.statusCode}")
                    return@thenCompose CompletableFuture.completedFuture(false)
                }
                CompletableFuture
                    .supplyAsync { joinServer(session, challenge.serverId) }
                    .thenCompose { joined ->
                        if (joined == JoinOutcome.UNAVAILABLE) {
                            // Network trouble or a Mojang outage says nothing about the account.
                            fail("Mojang session server unavailable")
                            return@thenCompose CompletableFuture.completedFuture(false)
                        }
                        if (joined == JoinOutcome.REJECTED) {
                            state = State.OFFLINE
                            lastError = "Mojang session rejected the join request"
                            inFlight = false
                            logger.info("Minecraft account link skipped: session is not a verified online account")
                            return@thenCompose CompletableFuture.completedFuture(false)
                        }
                        FPSMasterApiClient.confirmMinecraftLink(challenge.challengeId, session.username)
                            .thenApply { confirmation ->
                                val profile = confirmation.data
                                if (confirmation.success && profile != null && profile.minecraftUuid.isNotBlank()) {
                                    linkedUuid = parseUuid(profile.minecraftUuid)
                                    state = State.LINKED
                                    lastError = ""
                                    backoffMs = 0L
                                    inFlight = false
                                    logger.info("Minecraft account linked for cosmetics visibility")
                                    true
                                } else {
                                    fail(confirmation.code ?: "HTTP ${confirmation.statusCode}")
                                    false
                                }
                            }
                    }
            }
            .exceptionally { exception ->
                fail(exception.message ?: exception.javaClass.simpleName)
                false
            }
    }

    /** Signing out, or switching to another Minecraft account: the next world links afresh. */
    fun clear() {
        state = State.IDLE
        linkedUuid = null
        lastError = ""
        inFlight = false
        retryAt = 0L
        backoffMs = 0L
    }

    private fun fail(reason: String) {
        backoffMs = if (backoffMs == 0L) FIRST_BACKOFF_MS else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        retryAt = System.currentTimeMillis() + backoffMs
        state = State.FAILED
        lastError = reason
        inFlight = false
        logger.warn("Minecraft account link failed: {}; retrying in {}s", reason, backoffMs / 1000)
    }

    private fun currentSession(): Session? {
        val user = mc.user
        val token = user.accessToken
        val profileId = runCatching { user.profileId }.getOrNull() ?: return null
        if (token.isBlank() || user.name.isBlank()) {
            return null
        }
        return Session(token, profileId, user.name)
    }

    /**
     * Mojang `joinServer`; 204 means the session server accepted the (session, serverId) pair. Only
     * 401/403 mean the session itself is not an online account; anything else (no response, 429,
     * 5xx) is worth trying again later.
     */
    private fun joinServer(session: Session, serverId: String): JoinOutcome {
        val payload = JsonObject().apply {
            addProperty("accessToken", session.accessToken)
            addProperty("selectedProfile", undashed(session.profileId))
            addProperty("serverId", serverId)
        }
        val connection = URI.create(JOIN_URL).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "FPSMaster-Nova/${top.fpsmaster.Client.VERSION}")
            connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_OK -> JoinOutcome.ACCEPTED
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> JoinOutcome.REJECTED
                else -> JoinOutcome.UNAVAILABLE
            }
        } catch (exception: Exception) {
            // Never include the payload here: it carries the session access token.
            logger.warn("Mojang joinServer request failed: {}", exception.javaClass.simpleName)
            JoinOutcome.UNAVAILABLE
        } finally {
            connection.disconnect()
        }
    }

    private fun undashed(uuid: UUID): String = uuid.toString().replace("-", "")

    private fun parseUuid(value: String): UUID? = runCatching {
        if (value.contains('-')) {
            UUID.fromString(value.lowercase(Locale.ROOT))
        } else {
            UUID.fromString(
                value.lowercase(Locale.ROOT).replaceFirst(
                    Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"),
                    "$1-$2-$3-$4-$5"
                )
            )
        }
    }.getOrNull()

    private class Session(val accessToken: String, val profileId: UUID, val username: String)
}
