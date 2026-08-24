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
    private const val API_BASE_URL = "https://api.fpsmaster.top"
    private const val API_VERSION = "/api/v1"
    private val USER_AGENT = "FPSMaster-Nova/${top.fpsmaster.Client.VERSION}"
    private const val LAUNCHER_LOGIN = "$API_BASE_URL$API_VERSION/auth/launcher/login"
    private const val LOGOUT = "$API_BASE_URL$API_VERSION/auth/logout"
    private const val USER_INFO = "$API_BASE_URL$API_VERSION/user/info"
    private const val OWNED_ITEMS = "$API_BASE_URL$API_VERSION/me/items"
    private const val CATALOG_ITEMS = "$API_BASE_URL$API_VERSION/catalog/items"
    private const val PURCHASES = "$API_BASE_URL$API_VERSION/me/purchases"

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
                    ApiResult(false, "Logout request failed: ${exception.message}", Unit)
                } else {
                    AuthService.clearTokens()
                    currentUser = null
                    top.fpsmaster.cosmetic.CosmeticManager.refreshOwned()
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

    fun cachedUser(): UserInfo? = currentUser

    fun isLoggedIn(): Boolean = AuthService.isLoggedIn()

    private fun sendJson(url: String, payload: JsonObject?, authenticated: Boolean): CompletableFuture<HttpResponse<String>> {
        val builder = baseRequest(url, authenticated)
            .header("Content-Type", "application/json")

        val body = payload?.let { gson.toJson(it) } ?: ""
        return httpClient.sendAsync(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
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
        val body = response.body()
        if (body.isNullOrBlank()) {
            return ApiResult(false, "Empty response (${response.statusCode()})", null)
        }

        return runCatching {
            val json = gson.fromJson(body, JsonObject::class.java)
                ?: return ApiResult(false, "Invalid response (${response.statusCode()})", null)
            val success = json.booleanOrFalse("success") && response.statusCode() in 200..299
            val message = json.stringOrBlank("message").ifBlank {
                if (success) "OK" else "HTTP ${response.statusCode()}"
            }
            val data = if (json.has("data") && !json.get("data").isJsonNull && dataClass != Unit::class.java) {
                gson.fromJson(json.get("data"), dataClass)
            } else {
                null
            }
            ApiResult(success, message, data)
        }.getOrElse { exception ->
            logger.error("Failed to parse FPSMaster API response", exception)
            ApiResult(false, "Parse error: ${exception.message}", null)
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
    val data: T?
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
    val available: Boolean = false
)
