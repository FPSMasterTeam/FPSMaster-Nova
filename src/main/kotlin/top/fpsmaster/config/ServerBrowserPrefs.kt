package top.fpsmaster.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import top.fpsmaster.logger
import top.fpsmaster.mc
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader

/**
 * 多人游戏服务器浏览器的本地偏好：被玩家删掉的官方推荐服（按后端 id，老数据可能是地址），
 * 以及玩家置顶的自建服地址。独立于配置档案存放（`fpsmaster/server_browser.json`）——
 * 「删过的推荐服不再出现」是设备级决定，不该跟着配置档案切换而回来。
 *
 * 地址的归一化在 [top.fpsmaster.multiplayer.ServerBrowser] 做，这里只存取已归一化的字符串。
 */
object ServerBrowserPrefs {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var loaded = false
    private val hiddenPromoted = LinkedHashSet<String>()
    private val pinnedAddresses = LinkedHashSet<String>()

    fun isHidden(key: String): Boolean {
        ensureLoaded()
        return key in hiddenPromoted
    }

    fun hide(key: String) {
        ensureLoaded()
        if (hiddenPromoted.add(key)) {
            save()
        }
    }

    fun isPinned(address: String): Boolean {
        ensureLoaded()
        return address in pinnedAddresses
    }

    fun setPinned(address: String, pinned: Boolean) {
        ensureLoaded()
        val changed = if (pinned) pinnedAddresses.add(address) else pinnedAddresses.remove(address)
        if (changed) {
            save()
        }
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        loaded = true
        val path = filePath()
        if (!path.exists() || !path.isRegularFile()) {
            return
        }
        try {
            val file = path.reader().use { gson.fromJson(it, PrefsFile::class.java) } ?: return
            hiddenPromoted.addAll(file.hiddenPromoted.filter { it.isNotBlank() })
            pinnedAddresses.addAll(file.pinnedAddresses.filter { it.isNotBlank() })
        } catch (exception: JsonParseException) {
            logger.warn("Server browser prefs file is invalid, starting fresh: {}", path, exception)
        }
    }

    private fun save() {
        val path = filePath()
        Files.createDirectories(path.parent)
        // 与 ConfigManager 同一套「临时文件 + 原子替换」，坏一半的写入不会毁掉整份偏好。
        val temp = path.resolveSibling("${path.fileName}.${System.nanoTime()}.tmp")
        try {
            Files.writeString(temp, gson.toJson(PrefsFile(hiddenPromoted.toList(), pinnedAddresses.toList())))
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (exception: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: java.io.IOException) {
            logger.warn("Failed to save server browser prefs to {}", path, exception)
        } finally {
            temp.deleteIfExists()
        }
    }

    private fun filePath(): Path {
        return mc.gameDirectory.toPath().resolve("fpsmaster").resolve("server_browser.json")
    }

    private data class PrefsFile(
        val hiddenPromoted: List<String> = emptyList(),
        val pinnedAddresses: List<String> = emptyList()
    )
}
