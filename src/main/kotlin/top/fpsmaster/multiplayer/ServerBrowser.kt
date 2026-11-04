package top.fpsmaster.multiplayer

import top.fpsmaster.config.ServerBrowserPrefs
import java.util.Locale
import java.util.function.Function

/**
 * 原版多人游戏界面（JoinMultiplayerScreen）的 FPSMaster 增强：玩家可以把自己的服务器置顶。
 *
 * 排序和置顶判定都在这里；各版本 mixin 只负责把结果注入 vanilla 列表。所有读写都发生在
 * 客户端主线程（mixin 的调用点都在渲染/点击路径上）。
 *
 * 以前这里还会插入后端下发的推荐服，FPSMaster 服务器关闭后已删除。
 */
object ServerBrowser {
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
     * 多人列表的展示顺序：玩家置顶的服务器在前，其余在后，各自保持 servers.dat 顺序。
     *
     * 泛型是为了让排序逻辑独立于各 MC 版本的条目类型：mixin 提供「条目 → 地址」的函数。
     */
    fun <T> arrange(userEntries: List<T>, addressOf: Function<T, String?>): List<T> {
        val (pinned, rest) = userEntries.partition { isPinned(addressOf.apply(it)) }
        return pinned + rest
    }

    /**
     * The rows the multiplayer list last showed, in display order. Vanilla's move arrows and
     * Shift+Up/Down pass *display* positions to `ServerList.swap`; with pinned rows moved up those
     * are not servers.dat positions (MixinOnlineServerEntry maps them). Client thread only.
     */
    private var displayed: List<Any?> = emptyList()

    fun rememberDisplayed(rows: List<*>) {
        displayed = ArrayList(rows)
    }

    /** servers.dat position of display row [displayIndex], or -1 for an index outside the last arranged list. */
    fun storedIndex(stored: List<*>, displayIndex: Int): Int = storedIndex(displayed, stored, displayIndex)

    internal fun storedIndex(displayed: List<*>, stored: List<*>, displayIndex: Int): Int {
        val row = displayed.getOrNull(displayIndex) ?: return -1
        return stored.indexOfFirst { it === row }
    }

    /** 大小写、空白、显式默认端口都不算差别。 */
    private fun normalize(address: String?): String {
        val trimmed = address?.trim()?.lowercase(Locale.ROOT) ?: return ""
        return trimmed.removeSuffix(":25565")
    }
}
