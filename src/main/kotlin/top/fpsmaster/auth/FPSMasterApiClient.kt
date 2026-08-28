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
    // 后端没有 /user/info 这个资源（见 MeResource.kt 的 @Path("/api/v1/me")），
    // 以前那个路径恒 404，导致用户缓存永远填不上、每次都重试。
    private val USER_INFO = ApiBase.v1("/me")
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

    /** 防止「界面每帧调 refreshUserInfoAsync」变成每帧一个请求。 */
    private val profileRefreshing = java.util.concurrent.atomic.AtomicBoolean(false)

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
                    // 饰品/loadout 的拉取失败不能把「登录成功」这个结果打翻：这里抛出去的话
                    // thenApply 的 future 变成异常完成，登录界面会显示「无法连接服务器」，
                    // 而 token 其实已经存下来了，玩家看到的是自相矛盾的状态。
                    try {
                        top.fpsmaster.cosmetic.CosmeticManager.refreshOwned()
                        top.fpsmaster.cosmetic.CosmeticLoadoutClient.pull()
                    } catch (exception: Exception) {
                        logger.warn("Cosmetic refresh after login failed", exception)
                    }
                    logger.info("FPSMaster API login successful for {}", usernameOrEmail)
                }
                result
            }
    }

    fun logout(): CompletableFuture<ApiResult<Unit>> {
        // sendJson 的请求构造是同步的：token 里混进 CR/LF 之类的非法 header 字符时
        // `header()` 会当场抛。调用点（登录界面的登出按钮、`AuthCommand`）都是裸调，
        // 异常漏出去就是一份 crash report，而本地 token 一个都没清掉——玩家点了登出，
        // 结果客户端崩了，重开还是登录态。
        val pending = try {
            sendJson(LOGOUT, null, authenticated = true)
        } catch (exception: Exception) {
            logger.error("FPSMaster API logout request could not be started", exception)
            signOutLocally()
            return CompletableFuture.completedFuture(
                ApiResult(false, "Logout request failed: ${exception.message}", Unit)
            )
        }
        return pending
            .handle { response, exception ->
                if (exception != null) {
                    logger.error("FPSMaster API logout failed", exception)
                    signOutLocally()
                    ApiResult(false, "Logout request failed: ${exception.message}", Unit)
                } else {
                    signOutLocally()
                    val parsed = parseResponse(response, JsonObject::class.java)
                    ApiResult(parsed.success, parsed.message.ifBlank { "Logged out" }, Unit)
                }
            }
    }

    fun getUserInfo(): CompletableFuture<ApiResult<UserInfo>> {
        // 这次请求属于哪个 token 必须在发出前钉住：isLoggedIn() 只回答「现在有没有有效
        // token」，答不了「还是不是同一个账号」，换号之后旧响应照样会被写回缓存。
        val tokenAtRequest = AuthService.accessToken
        return sendGet(USER_INFO, authenticated = true)
            .thenApply { response ->
                // /api/v1/me 返回的是后端的 UserView：id 是 UUID 字符串、createdAt 是 ISO 时间串，
                // 而 UserInfo 把这两个字段声明成 Long，直接按 UserInfo 解析会在 Gson 层抛异常。
                // 登录响应里的 CurrentUserView 就是同一个形状，复用它再走同一套映射。
                val result = parseResponse(response, CurrentUserView::class.java)
                val view = result.data
                if (result.success && view != null) {
                    val info = view.toUserInfo()
                    // 这次请求发出之后玩家可能已经登出、甚至换了账号：不判一下就会把别人的
                    // 用户名写回缓存，界面继续显示它直到重启。
                    if (tokenStillCurrent(tokenAtRequest)) {
                        currentUser = info
                    }
                    ApiResult(true, result.message, info, result.statusCode, result.serverMessage)
                } else {
                    ApiResult(false, result.message, null, result.statusCode, result.serverMessage)
                }
            }
    }

    /**
     * 已登录但缓存是空的时候异步补一次 profile；正在补的时候不重复发。
     *
     * 界面每帧都会问名字，所以取名字那条路径必须是纯读（[cachedUser]），刷新只走这里。
     *
     * 缓存已经有值就直接返回：调用点在 `init()` 里，而 MC 每次改变窗口大小都会重跑
     * `init()`，不判空的话拖一下窗口就是好几个白发的请求。
     */
    fun refreshUserInfoAsync() {
        if (!isLoggedIn() || currentUser != null || !profileRefreshing.compareAndSet(false, true)) {
            return
        }
        // [getUserInfo] 的请求构造（URI 解析、`Authorization` 头校验）是同步跑在调用者
        // 线程上的：token 里混进 CR/LF 之类的非法 header 字符时 `header()` 会当场抛，
        // 而调用点是 `Screen.init()`——MC 不守卫它，会直接崩到 crash report，
        // 并且 profileRefreshing 已经 CAS 成 true、本进程再也刷不到 profile。
        val tokenAtRequest = AuthService.accessToken
        try {
            getUserInfo().whenComplete { result, exception ->
                try {
                    if (exception != null) {
                        logger.warn("Profile refresh failed", exception)
                        return@whenComplete
                    }
                    // token 已被服务端吊销时 isLoggedIn() 仍是 true（它只看本地过期时间），
                    // 于是界面一直显示「未知账号」。把本地凭据清掉，回落到未登录态。
                    //
                    // 只认 401。/api/v1/me 的 403 只有「账号被封」一条出口（CurrentUser.requireUser），
                    // 而封禁可能是临时的；再加上 CDN/WAF 的挑战页也是 403，把凭据销毁掉等于让玩家
                    // 莫名其妙掉线、封禁到期还得重新输密码。
                    //
                    // 还要确认被拒的就是当前这个 token：迟到的 401 落在重新登录之后，
                    // 会把刚拿到的新凭据连同 auth.json 一起抹掉，而界面上没有任何提示。
                    if (result != null && !result.success && result.statusCode == 401 &&
                        tokenStillCurrent(tokenAtRequest)
                    ) {
                        signOutLocally()
                        logger.warn("Stored credentials rejected by server, signed out locally")
                    }
                } finally {
                    profileRefreshing.set(false)
                }
            }
        } catch (exception: Exception) {
            // 只接 Exception：OOM / StackOverflow 这类 Error 不该在这里被吞掉，
            // 吞了之后 MC 会带着一个已经损坏的运行时继续跑。
            profileRefreshing.set(false)
            logger.warn("Profile refresh could not be started", exception)
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

    /**
     * 发起请求时钉住的 token 是不是仍然是当前 token。
     *
     * 换号、登出、乃至续签都会让它变，此时那条在途请求的结果就不再属于「现在这个人」。
     */
    private fun tokenStillCurrent(tokenAtRequest: String?): Boolean {
        return !tokenAtRequest.isNullOrBlank() && tokenAtRequest == AuthService.accessToken
    }

    /**
     * 只清本地会话，不发登出请求。
     *
     * [logout] 和「服务端已吊销 token」两条路必须做完全一样的清理，否则账号 UI 回到未登录态、
     * 饰品却还挂着上一个账号的已拥有列表和 loadout，后续同步全是无 token 的 401。
     */
    private fun signOutLocally() {
        AuthService.clearTokens()
        currentUser = null
        top.fpsmaster.cosmetic.CosmeticManager.refreshOwned()
        clearCosmeticSession()
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
            val rawMessage = json.stringOrBlank("message")
            val message = rawMessage.ifBlank {
                if (success) "OK" else "HTTP ${response.statusCode()}"
            }
            val data = if (json.has("data") && !json.get("data").isJsonNull && dataClass != Unit::class.java) {
                gson.fromJson(json.get("data"), dataClass)
            } else {
                null
            }
            // 「后端按契约给的原话」得连信封一起认：`success` 字段在才算契约响应。
            // Cloudflare 之类挡在前面时正文可能是一段带 message 的 JSON，只看 message
            // 非空就会把它当成封禁原因贴到界面上。
            ApiResult(success, message, data, status, rawMessage.isNotBlank() && json.has("success"))
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
    val statusCode: Int = 0,
    /**
     * [message] 是不是后端按契约给的原话。
     *
     * 解析失败、空正文这些兜底路径也会填一个 message（`Parse error: ...`），直接贴到界面上
     * 就是一句英文技术黑话。UI 想「优先显示后端原话」时必须先问这个，否则 Cloudflare
     * 挡下来的 403 会被当成封禁原因显示出去。
     */
    val serverMessage: Boolean = false
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
