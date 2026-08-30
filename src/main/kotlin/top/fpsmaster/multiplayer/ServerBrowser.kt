package top.fpsmaster.multiplayer

import top.fpsmaster.auth.FPSMasterApiClient
import top.fpsmaster.config.ServerBrowserPrefs
import top.fpsmaster.logger
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

/**
 * 原版多人游戏界面（JoinMultiplayerScreen）的 FPSMaster 增强：后端推荐服置顶展示、
 * 推荐服可删（本地隐藏、重启仍生效）、玩家可置顶自建服。
 *
 * 这里是全部业务决策——排序、同地址去重、隐藏与置顶的判定；各版本 mixin 只负责把
 * 结果注入 vanilla 列表。所有读写都发生在客户端主线程（mixin 的调用点都在渲染/点击
 * 路径上），只有 [promoted] 缓存由 HTTP 回调线程写入，故为 @Volatile。
 */
object ServerBrowser {
    data class PromotedServer(
        val id: String,
        val name: String,
        val address: String,
        val description: String
    )

    @Volatile
    private var promoted: List<PromotedServer> = emptyList()
    private val fetchInFlight = AtomicBoolean(false)

    @Volatile
    private var lastFetchAt = 0L

    /**
     * 异步拉一次推荐服列表；结果与缓存不同的时候在 HTTP 回调线程上跑 [onChanged]
     * （调用方负责切回客户端线程）。请求失败或后端返回异常时缓存原样保留——
     * 玩家自己的服务器列表不受任何影响。
     *
     * 30 秒节流：调用点在 `JoinMultiplayerScreen.init`，而 MC 每次改窗口大小都会重跑
     * init，不节流的话拖一下窗口就是一串请求（同 FPSMasterApiClient.refreshProfileIfStale）。
     */
    fun refreshAsync(onChanged: Runnable) {
        val last = lastFetchAt
        if (last != 0L && System.currentTimeMillis() - last in 0 until 30_000) {
            return
        }
        if (!fetchInFlight.compareAndSet(false, true)) {
            return
        }
        lastFetchAt = System.currentTimeMillis()
        // 请求构造是同步跑在调用者线程上的（URI 解析等），当场抛的话闸门必须放开，
        // 而调用点是 Screen.init —— 异常漏出去就是一份 crash report。
        val pending = try {
            FPSMasterApiClient.getLauncherServers()
        } catch (exception: Exception) {
            fetchInFlight.set(false)
            logger.warn("Promoted server list request could not be started", exception)
            return
        }
        pending.whenComplete { result, exception ->
            fetchInFlight.set(false)
            if (exception != null) {
                logger.warn("Promoted server list fetch failed", exception)
                return@whenComplete
            }
            if (result?.success != true || result.data == null) {
                logger.warn("Promoted server list fetch rejected: {}", result?.message ?: "no response")
                return@whenComplete
            }
            val fresh = result.data
                .filter { it.active && !it.address.isNullOrBlank() }
                .map {
                    PromotedServer(
                        id = it.id.orEmpty(),
                        name = it.name?.takeIf { name -> name.isNotBlank() } ?: it.address.orEmpty(),
                        address = it.address.orEmpty(),
                        description = it.description.orEmpty()
                    )
                }
            if (fresh != promoted) {
                promoted = fresh
                onChanged.run()
            }
        }
    }

    /** 当前应当展示的推荐服：active、未被本地隐藏，保持后端顺序。 */
    fun visiblePromoted(): List<PromotedServer> {
        return promoted.filter { !isHidden(it) }
    }

    /**
     * 这个地址在列表里是不是以「推荐服」的身份展示。
     * 玩家置顶的同地址自建服优先（同地址去重规则），所以被置顶的地址永远不算推荐。
     */
    fun shownAsPromoted(address: String?): Boolean {
        val key = normalize(address)
        if (key.isEmpty() || isPinned(address)) {
            return false
        }
        return visiblePromoted().any { normalize(it.address) == key }
    }

    fun isPinned(address: String?): Boolean {
        val key = normalize(address)
        return key.isNotEmpty() && ServerBrowserPrefs.isPinned(key)
    }

    fun togglePin(address: String?) {
        val key = normalize(address)
        if (key.isNotEmpty()) {
            ServerBrowserPrefs.setPinned(key, !ServerBrowserPrefs.isPinned(key))
        }
    }

    /**
     * 玩家删除了这个地址上展示的推荐服：按后端 id 记住（id 缺失时退回地址），重启仍隐藏。
     */
    fun hidePromoted(address: String?) {
        val key = normalize(address)
        if (key.isEmpty()) {
            return
        }
        val target = visiblePromoted().firstOrNull { normalize(it.address) == key } ?: return
        ServerBrowserPrefs.hide(target.id.ifBlank { key })
    }

    /**
     * 计算多人列表的展示顺序：玩家置顶的自建服（保持 servers.dat 顺序）→ 未隐藏的推荐服
     * （保持后端顺序）→ 其余自建服。同地址去重：被置顶的地址不再出推荐条目；未置顶但与
     * 推荐服同地址的自建服则让位给推荐条目（隐藏该推荐服后自建服会回来）。
     *
     * 泛型 + 工厂是为了让排序逻辑独立于各 MC 版本的条目类型：mixin 提供「条目 → 地址」
     * 和「推荐服 → 条目」两个函数，业务判断全部留在这里。
     */
    fun <T> arrange(
        userEntries: List<T>,
        addressOf: Function<T, String?>,
        promotedEntryFactory: Function<PromotedServer, T>
    ): List<T> {
        val pinned = ArrayList<T>()
        val rest = ArrayList<T>()
        for (entry in userEntries) {
            if (isPinned(addressOf.apply(entry))) {
                pinned.add(entry)
            } else {
                rest.add(entry)
            }
        }
        val pinnedAddresses = pinned.mapTo(HashSet()) { normalize(addressOf.apply(it)) }
        val shownPromoted = visiblePromoted().filter { normalize(it.address) !in pinnedAddresses }
        val promotedAddresses = shownPromoted.mapTo(HashSet()) { normalize(it.address) }
        val shownRest = rest.filter { normalize(addressOf.apply(it)) !in promotedAddresses }

        val arranged = ArrayList<T>(pinned.size + shownPromoted.size + shownRest.size)
        arranged.addAll(pinned)
        shownPromoted.mapTo(arranged) { promotedEntryFactory.apply(it) }
        arranged.addAll(shownRest)
        return arranged
    }

    private fun isHidden(server: PromotedServer): Boolean {
        return (server.id.isNotBlank() && ServerBrowserPrefs.isHidden(server.id)) ||
            ServerBrowserPrefs.isHidden(normalize(server.address))
    }

    /** 玩家手输的地址和后端下发的地址要能对上：大小写、空白、显式默认端口都不算差别。 */
    private fun normalize(address: String?): String {
        val trimmed = address?.trim()?.lowercase(Locale.ROOT) ?: return ""
        return trimmed.removeSuffix(":25565")
    }
}
