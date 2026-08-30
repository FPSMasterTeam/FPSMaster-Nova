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
    private val LAUNCHER_SERVERS = ApiBase.v1("/launcher/servers")

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

    /**
     * 闸门关着时又来了一次强制刷新的请求，记在这里，等在途那发落地后补发。
     *
     * 丢掉它是不行的：在途那发很可能是「买之前」发出的，它带回来的是扣款前的余额，
     * 而下单成功后那次刷新恰恰是唯一能把表头改对的机会。
     */
    private val profileRefreshPending = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 每发一次 profile 请求就 +1；写回缓存时用它判「这份响应是不是最新的那发」。 */
    private val profileRequestSeq = java.util.concurrent.atomic.AtomicLong(0)

    /** 已经写回缓存的那发的序号。 */
    private val profileAppliedSeq = java.util.concurrent.atomic.AtomicLong(0)

    /** 本线程是否已经在 [pumpProfileRefresh] 的循环里，用来把补发从递归压成循环。 */
    private val profilePumping: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    /** 是否有一张订单在途。进程级，见 [purchaseItem]。 */
    private val purchaseInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 最近一次 profile 请求的发起时刻，[refreshProfileIfStale] 的节流依据。 */
    @Volatile
    private var lastProfileFetchAt = 0L

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
                    // 和 /me 的响应抢同一个缓存：登录响应永远是「更新的那份」，
                    // 所以也得占一个序号，免得一发在途的旧 /me 把它顶掉。
                    // 只在真有 user 的时候写：后端只回 token 不回 user（契约漂移、字段裁剪）
                    // 时把缓存清成 null 的话，表头的用户名和余额会一起变空白，而且这一下还
                    // 白烧掉一个序号——在途那发 /me 序号更小，回来会被当成「旧的」丢掉。
                    val fresh = data.user?.toUserInfo()
                    if (fresh != null && claimProfileSeq(profileRequestSeq.incrementAndGet())) {
                        currentUser = fresh
                    }
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
        // 序号在**发出前**取：两发 profile 并行时先发的完全可能后到（连接慢、重试），
        // 它带的是扣款前的余额，写回去就把刚下单刷新的新余额顶掉了，而且从此不会再有人来纠正。
        val seq = profileRequestSeq.incrementAndGet()
        lastProfileFetchAt = System.currentTimeMillis()
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
                    if (tokenStillCurrent(tokenAtRequest) && claimProfileSeq(seq)) {
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
        startProfileFetch()
    }

    /**
     * 无视缓存强制补一次 profile（余额会变：买完东西、网站上充值）。
     *
     * 和 [refreshUserInfoAsync] 共用同一个闸门，区别在于：缓存已有值照样发，而且闸门
     * 关着时不是把这次刷新丢掉，而是记一笔、等在途那发落地后补发（见 [profileRefreshPending]）。
     * 调用点必须是一次性的（下单成功、手动刷新），放进 `Screen.init()` 会变成一串请求——
     * 那种场景用 [refreshProfileIfStale]。
     */
    fun refreshProfileNow() {
        if (!isLoggedIn()) {
            return
        }
        if (!profileRefreshing.compareAndSet(false, true)) {
            profileRefreshPending.set(true)
            // 记完再抢一次闸门：在途那发正好在「CAS 失败」和「置 pending」这两句之间
            // 释放的话，它读到的 pending 还是 false，没人会替我们补发。
            if (!profileRefreshing.compareAndSet(false, true)) {
                return
            }
            profileRefreshPending.set(false)
        }
        startProfileFetch()
    }

    /**
     * 距上次发起 profile 请求超过 [maxAgeMs] 才发一次。
     *
     * 给 `Screen.init()` 用：MC 每次 resize 都会重跑 init，而登录界面用的是同一个 parent
     * 实例返回、构造函数不会再跑一遍，所以「开界面就刷」这件事只能挂在 init 上。
     */
    fun refreshProfileIfStale(maxAgeMs: Long = 10_000L) {
        if (!isLoggedIn()) {
            return
        }
        val last = lastProfileFetchAt
        // 只看「距上次**发起**多久」，不看缓存里有没有东西。加一条 currentUser != null 会让
        // 节流在最需要它的时候失效：/me 持续失败（后端 5xx、断网、挑战页）时缓存一直是 null，
        // 于是每次 resize 重跑 init() 都直落一发请求，拖一次窗口就是几十发。
        //
        // 时钟回拨（NTP 校时、休眠唤醒）时 now - last 会变成负数，那就当成过期，宁可多发一次。
        if (last != 0L && System.currentTimeMillis() - last in 0 until maxAgeMs) {
            return
        }
        refreshProfileNow()
    }

    /**
     * 真正发一次 /me，并在结束时释放闸门。调用前必须已经拿到 [profileRefreshing]。
     *
     * [getUserInfo] 的请求构造（URI 解析、`Authorization` 头校验）是同步跑在调用者线程上的：
     * token 里混进 CR/LF 之类的非法 header 字符时 `header()` 会当场抛，而调用点是
     * `Screen.init()`——MC 不守卫它，会直接崩到 crash report，并且闸门再也开不回来。
     */
    private fun startProfileFetch() {
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
                    releaseProfileGate()
                }
            }
        } catch (exception: Exception) {
            // 只接 Exception：OOM / StackOverflow 这类 Error 不该在这里被吞掉，
            // 吞了之后 MC 会带着一个已经损坏的运行时继续跑。
            releaseProfileGate()
            logger.warn("Profile refresh could not be started", exception)
        }
    }

    /** 开闸；期间被压下的强制刷新交给 [pumpProfileRefresh] 补发。 */
    private fun releaseProfileGate() {
        profileRefreshing.set(false)
        if (!profileRefreshPending.get()) {
            return
        }
        if (profilePumping.get()) {
            // 这一发是本线程的 pump 循环发出去的、而且同步就落地了（future 已完成、
            // 或者请求构造当场抛）。在这里补发等于纯栈递归，深度只受并发方置 pending 的
            // 速率限制，迟早 StackOverflowError。交回给那个循环接力。
            return
        }
        pumpProfileRefresh()
    }

    /**
     * 补发被压下的强制刷新——循环而不是递归。
     *
     * 补发那一发完全可能**同步**落地（[getUserInfo] 的请求构造在调用者线程上跑，token 里
     * 混进非法 header 字符会当场抛；HttpClient 被关时 future 也是已完成的），那时
     * [releaseProfileGate] 会在同一个栈上再调回来。
     */
    private fun pumpProfileRefresh() {
        profilePumping.set(true)
        try {
            while (profileRefreshPending.compareAndSet(true, false)) {
                if (!profileRefreshing.compareAndSet(false, true)) {
                    // 闸门被别人抢走了：把笔记放回去，由那一发落地时接力。最坏情况是
                    // 多发一次（那一发本来就覆盖了这条笔记），不会漏发。
                    profileRefreshPending.set(true)
                    return
                }
                startProfileFetch()
            }
        } finally {
            profilePumping.set(false)
        }
    }

    /**
     * 只让「发得更晚」的那份响应写回缓存，返回是否拿到了写入权。
     *
     * 光有 [tokenStillCurrent] 不够：它只回答「还是不是同一个账号」，答不了「是不是更新的
     * 那一份」。同一个账号并行两发 profile 时，先发后到的那份会把新余额顶回旧值。
     */
    private fun claimProfileSeq(seq: Long): Boolean {
        while (true) {
            val applied = profileAppliedSeq.get()
            if (seq <= applied) {
                return false
            }
            if (profileAppliedSeq.compareAndSet(applied, seq)) {
                return true
            }
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

    /**
     * 下单。已经有一单在途时返回 null，一分钱都不会再发出去。
     *
     * 闸门放在这里而不是界面上：界面上的标志位是随界面新建的，玩家在响应回来之前按 ESC
     * 退出饰品界面再进去，标志位就回到 false，「确认购买」当场又能点了——后端收到两张订单，
     * 扣两次钱。放在客户端单例上，关界面、开另一个界面、甚至换个入口进来都拦得住。
     *
     * 闸门在 future 上自己解，调用方忘不了。
     */
    fun purchaseItem(itemId: Long): CompletableFuture<ApiResult<JsonObject>>? {
        if (!purchaseInFlight.compareAndSet(false, true)) {
            return null
        }
        val payload = JsonObject().apply { addProperty("itemId", itemId) }
        val request = try {
            sendJson(PURCHASES, payload, authenticated = true)
        } catch (failure: Throwable) {
            // 请求构造是同步跑的（URI 解析、header 校验），当场抛出来的话闸门得放开，
            // 否则这个客户端进程从此再也下不了单。
            purchaseInFlight.set(false)
            // 往外抛就是抛进渲染栈：调用点是饰品界面里「确认购买」那一下，异常从 draw 一路
            // 冒到客户端主循环，玩家看到的是崩溃报告而不是一行「购买失败」。调用方本来就在
            // whenComplete 里处理异常完成，交给它。
            return CompletableFuture.failedFuture(failure)
        }
        return request
            .thenApply { response -> parseResponse(response, JsonObject::class.java) }
            .whenComplete { _, _ -> purchaseInFlight.set(false) }
    }

    /** 有没有订单在途。界面拿它决定购买按钮画成「下单中」。 */
    val purchaseInProgress: Boolean
        get() = purchaseInFlight.get()

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

    /** 官方推荐/合作服务器列表（公开端点，后端只返回启用中的条目）。 */
    fun getLauncherServers(): CompletableFuture<ApiResult<Array<LauncherServerView>>> {
        return sendGet(LAUNCHER_SERVERS, authenticated = false)
            .thenApply { response -> parseResponse(response, Array<LauncherServerView>::class.java) }
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
        // 节流基准也得清：10 秒内登出再登另一个账号的话，新账号那次 refreshProfileIfStale
        // 会被上一个账号的时间戳挡掉，界面停在空白余额上等下一次触发。
        lastProfileFetchAt = 0L
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
            walletBalance = walletBalance,
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
    /** 钱包余额，和商品 `price` 同口径的十进制字符串。null＝后端没给。 */
    val walletBalance: String? = null,
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

/** `GET /api/v1/launcher/servers` 的单项：官方推荐/合作服务器。未知新字段由 Gson 忽略。 */
data class LauncherServerView(
    val id: String? = null,
    val name: String? = null,
    val address: String? = null,
    val description: String? = null,
    val active: Boolean = true
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
