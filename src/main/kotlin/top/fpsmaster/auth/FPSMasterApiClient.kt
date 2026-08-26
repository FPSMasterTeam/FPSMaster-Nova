package top.fpsmaster.auth

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import top.fpsmaster.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object FPSMasterApiClient {
    private val USER_AGENT = "FPSMaster-Nova/${top.fpsmaster.Client.VERSION}"
    private val LAUNCHER_LOGIN = ApiBase.v1("/auth/launcher/login")
    private val LOGOUT = ApiBase.v1("/auth/logout")
    private val USER_INFO = ApiBase.v1("/user/info")
    private val OWNED_ITEMS = ApiBase.v1("/me/items")
    private val CATALOG_ITEMS = ApiBase.v1("/catalog/items")
    private val PURCHASES = ApiBase.v1("/me/purchases")
    private val COSMETIC_LOADOUT = ApiBase.v1("/me/cosmetics/loadout")
    private val LOADOUT_RESOLVE = ApiBase.v1("/cosmetics/loadouts/resolve")
    private val LINK_CHALLENGE = ApiBase.v1("/me/minecraft-links/challenge")
    private val LINK_CONFIRM = ApiBase.v1("/me/minecraft-links/confirm")

    /** Backend cap for one resolve call. */
    const val RESOLVE_BATCH_LIMIT = 200

    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .disableHtmlEscaping()
        .create()

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    @Volatile
    private var currentUser: UserInfo? = null

    fun login(usernameOrEmail: String, password: String): CompletableFuture<ApiResult<LoginResponse>> {
        val payload = JsonObject().apply {
            addProperty("usernameOrEmail", usernameOrEmail)
            addProperty("password", password)
        }

        return sendJson(LAUNCHER_LOGIN, payload, authenticated = false)
            .thenApply { response ->
                val result = parseResponse(response, LoginResponse::class.java)
                val data = result.data
                if (result.success && data?.token?.isNotBlank() == true) {
                    AuthService.saveTokens(data.token, null)
                    currentUser = data.user?.toUserInfo()
                    top.fpsmaster.cosmetic.CosmeticManager.refreshOwned()
                    top.fpsmaster.cosmetic.CosmeticLoadoutClient.pull()
                    logger.info("FPSMaster API login successful for {}", usernameOrEmail)
                }
                result
            }
    }

    fun logout(): CompletableFuture<ApiResult<Unit>> {
        return sendJson(LOGOUT, null, authenticated = true)
            .handle { response, exception ->
                if (exception != null) {
                    logger.error("FPSMaster API logout failed", exception)
                    AuthService.clearTokens()
                    currentUser = null
                    top.fpsmaster.cosmetic.CosmeticManager.refreshOwned()
                    clearCosmeticSession()
                    ApiResult(false, "Logout request failed: ${exception.message}", Unit)
                } else {
                    AuthService.clearTokens()
                    currentUser = null
                    top.fpsmaster.cosmetic.CosmeticManager.refreshOwned()
                    clearCosmeticSession()
                    val parsed = parseResponse(response, JsonObject::class.java)
                    ApiResult(parsed.success, parsed.message.ifBlank { "Logged out" }, Unit)
                }
            }
    }

    fun getUserInfo(): CompletableFuture<ApiResult<UserInfo>> {
        return sendGet(USER_INFO, authenticated = true)
            .thenApply { response ->
                val result = parseResponse(response, UserInfo::class.java)
                if (result.success && result.data != null) {
                    currentUser = result.data
                }
                result
            }
    }

    fun getOwnedItems(): CompletableFuture<ApiResult<Array<OwnedItemView>>> {
        return sendGet(OWNED_ITEMS, authenticated = true)
            .thenApply { response -> parseResponse(response, Array<OwnedItemView>::class.java) }
    }

    fun getCatalogItems(): CompletableFuture<ApiResult<Array<ItemView>>> {
        return sendGet(CATALOG_ITEMS, authenticated = false)
            .thenApply { response -> parseResponse(response, Array<ItemView>::class.java) }
    }

    fun purchaseItem(itemId: Long): CompletableFuture<ApiResult<JsonObject>> {
        val payload = JsonObject().apply { addProperty("itemId", itemId) }
        return sendJson(PURCHASES, payload, authenticated = true)
            .thenApply { response -> parseResponse(response, JsonObject::class.java) }
    }

    fun getCosmeticLoadout(): CompletableFuture<ApiResult<CosmeticLoadoutView>> {
        return sendGet(COSMETIC_LOADOUT, authenticated = true)
            .thenApply { response -> parseResponse(response, CosmeticLoadoutView::class.java) }
    }

    fun putCosmeticLoadout(request: CosmeticLoadoutRequest): CompletableFuture<ApiResult<CosmeticLoadoutView>> {
        val payload = JsonObject().apply {
            if (request.capeItemId == null) add("capeItemId", null) else addProperty("capeItemId", request.capeItemId)
            if (request.backItemId == null) add("backItemId", null) else addProperty("backItemId", request.backItemId)
            addProperty("builtinWingsEnabled", request.builtinWingsEnabled)
            addProperty("wingScale", request.wingScale)
            addProperty("capeAnimationEnabled", request.capeAnimationEnabled)
        }
        return sendJson(COSMETIC_LOADOUT, payload, authenticated = true, method = "PUT")
            .thenApply { response -> parseResponse(response, CosmeticLoadoutView::class.java) }
    }

    /**
     * Batch lookup of other players' loadouts by Mojang-verified UUID. The backend caps the batch at
     * 200 canonical UUIDs and answers with loadouts only — never with account data.
     */
    fun resolveLoadouts(minecraftUuids: Collection<String>): CompletableFuture<ApiResult<Array<ResolvedLoadoutView>>> {
        val payload = JsonObject().apply {
            add("minecraftUuids", gson.toJsonTree(minecraftUuids.take(RESOLVE_BATCH_LIMIT)))
        }
        return sendJson(LOADOUT_RESOLVE, payload, authenticated = true)
            .thenApply { response -> parseResponse(response, Array<ResolvedLoadoutView>::class.java) }
    }

    fun createMinecraftLinkChallenge(): CompletableFuture<ApiResult<MinecraftLinkChallengeView>> {
        return sendJson(LINK_CHALLENGE, null, authenticated = true)
            .thenApply { response -> parseResponse(response, MinecraftLinkChallengeView::class.java) }
    }

    fun confirmMinecraftLink(challengeId: String, username: String): CompletableFuture<ApiResult<MinecraftProfileView>> {
        val payload = JsonObject().apply {
            addProperty("challengeId", challengeId)
            addProperty("username", username)
        }
        return sendJson(LINK_CONFIRM, payload, authenticated = true)
            .thenApply { response -> parseResponse(response, MinecraftProfileView::class.java) }
    }

    /** Signing out drops every cosmetic identity: own loadout sync, other players' loadouts, the link. */
    private fun clearCosmeticSession() {
        top.fpsmaster.cosmetic.CosmeticLoadoutClient.clear()
        top.fpsmaster.cosmetic.CosmeticLoadoutCache.clear()
        MinecraftLinkClient.clear()
    }

    fun cachedUser(): UserInfo? = currentUser

    fun isLoggedIn(): Boolean = AuthService.isLoggedIn()

    private fun sendJson(
        url: String,
        payload: JsonObject?,
        authenticated: Boolean,
        method: String = "POST"
    ): CompletableFuture<HttpResponse<String>> {
        val builder = baseRequest(url, authenticated)
            .header("Content-Type", "application/json")

        val body = payload?.let { gson.toJson(it) } ?: ""
        return httpClient.sendAsync(
            builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString()
        )
    }

    private fun sendGet(url: String, authenticated: Boolean): CompletableFuture<HttpResponse<String>> {
        return httpClient.sendAsync(baseRequest(url, authenticated).GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun baseRequest(url: String, authenticated: Boolean): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")

        if (authenticated) {
            val token = AuthService.accessToken
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        return builder
    }

    private fun <T> parseResponse(response: HttpResponse<String>, dataClass: Class<T>): ApiResult<T> {
        val status = response.statusCode()
        val body = response.body()
        if (body.isNullOrBlank()) {
            return ApiResult(false, "Empty response ($status)", null, status)
        }

        return runCatching {
            val json = gson.fromJson(body, JsonObject::class.java)
                ?: return ApiResult(false, "Invalid response ($status)", null, status)
            val success = json.booleanOrFalse("success") && response.statusCode() in 200..299
            val message = json.stringOrBlank("message").ifBlank {
                if (success) "OK" else "HTTP ${response.statusCode()}"
            }
            val data = if (json.has("data") && !json.get("data").isJsonNull && dataClass != Unit::class.java) {
                gson.fromJson(json.get("data"), dataClass)
            } else {
                null
            }
            ApiResult(success, message, data, status)
        }.getOrElse { exception ->
            logger.error("Failed to parse FPSMaster API response", exception)
            ApiResult(false, "Parse error: ${exception.message}", null, status)
        }
    }

    private fun JsonObject.stringOrBlank(name: String): String {
        val element = get(name)
        return if (element == null || element.isJsonNull) "" else element.asString
    }

    private fun JsonObject.booleanOrFalse(name: String): Boolean {
        val element = get(name)
        return element != null && !element.isJsonNull && element.asBoolean
    }
}

data class ApiResult<T>(
    val success: Boolean,
    val message: String,
    val data: T?,
    /** HTTP status; 0 when the request never produced a response. 404 means "endpoint not deployed". */
    val statusCode: Int = 0
)

data class LoginResponse(
    val token: String? = null,
    val user: CurrentUserView? = null
)

data class CurrentUserView(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val role: String? = null,
    val avatarUrl: String? = null,
    val banned: Boolean = false,
    val walletBalance: String? = null,
    val level: Int = 0,
    val experience: Int = 0,
    val nextLevelNeed: Int = 0,
    val checkedInToday: Boolean = false,
    val lastCheckInDate: String? = null,
    val customTitle: String? = null,
    val sponsorTitleClaimed: Boolean = false,
    val novaBetaEligible: Boolean = false,
    val emailVerified: Boolean = false,
    val membershipExpiresAt: String? = null
) {
    fun toUserInfo(): UserInfo {
        return UserInfo(
            id = id?.toLongOrNull(),
            username = username,
            email = email,
            displayName = username,
            avatar = avatarUrl,
            level = level,
            exp = experience.toLong(),
            emailVerified = emailVerified
        )
    }
}

data class UserInfo(
    val id: Long? = null,
    val username: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val level: Int? = null,
    val exp: Long? = null,
    val premium: Boolean? = null,
    val createdAt: Long? = null,
    val emailVerified: Boolean? = null
)

data class OwnedItemView(
    val ownershipId: String = "",
    val acquiredAt: String = "",
    val item: ItemView = ItemView()
)

data class ItemView(
    val id: Long = -1,
    val category: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val assetKey: String = "",
    val price: String = "0",
    val available: Boolean = false,
    /** Per-item scale policy. Decimals travel as strings, like `price`. Non-wings are 1.00/false/1.00/1.00. */
    val scale: String = "1.00",
    val allowResize: Boolean = false,
    val minScale: String = "1.00",
    val maxScale: String = "1.00"
) {
    fun scaleValue(): Float = scale.toFloatOrNull() ?: 1f

    fun minScaleValue(): Float = minScale.toFloatOrNull() ?: 1f

    fun maxScaleValue(): Float = maxScale.toFloatOrNull() ?: 1f
}

/**
 * A stored loadout. Item ids are null when nothing is equipped in that slot, and the embedded
 * [capeItem]/[backItem] save a catalog round trip when this loadout belongs to another player.
 */
data class CosmeticLoadoutView(
    val capeItemId: Long? = null,
    val backItemId: Long? = null,
    val builtinWingsEnabled: Boolean = false,
    val capeAnimationEnabled: Boolean = false,
    val wingScale: String = "1.00",
    val capeItem: ItemView? = null,
    val backItem: ItemView? = null
) {
    fun wingScaleValue(): Float = wingScale.toFloatOrNull() ?: 1f
}

/** Body of a loadout write. Null ids clear the slot, so they are serialised explicitly. */
data class CosmeticLoadoutRequest(
    val capeItemId: Long?,
    val backItemId: Long?,
    val builtinWingsEnabled: Boolean,
    val wingScale: Float,
    val capeAnimationEnabled: Boolean
)

/** One entry of a batch resolve. [loadout] is null for a player who has none. */
data class ResolvedLoadoutView(
    val minecraftUuid: String = "",
    val loadout: CosmeticLoadoutView? = null
)

/** Challenge to prove Minecraft account ownership: join [serverId] with the Mojang session, then confirm. */
data class MinecraftLinkChallengeView(
    val challengeId: String = "",
    val serverId: String = ""
)

data class MinecraftProfileView(
    val minecraftUuid: String = "",
    val username: String = ""
)
