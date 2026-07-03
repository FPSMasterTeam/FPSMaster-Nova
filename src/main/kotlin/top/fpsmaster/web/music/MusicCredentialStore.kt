package top.fpsmaster.web.music

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import top.fpsmaster.logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader
import kotlin.io.path.writer

/**
 * 音乐登录凭证的磁盘持久化。
 *
 * 与 [top.fpsmaster.auth.AuthService] 同目录（`%APPDATA%/FPSMaster/` 或 `~/.fpsmaster/`），
 * 单独存 `music_auth.json`。凭证是每用户的敏感数据，不放进会同步到前端的 ConfigManager 里。
 *
 * 存：网易云 cookie（含 MUSIC_U）、QQ 的 musicid + musickey(qm_keyst)。
 */
object MusicCredentialStore {
    private const val FILE_NAME = "music_auth.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file: Path = resolveFile()

    var neteaseCookie: String = ""
        private set
    var qqMusicId: String = ""
        private set
    var qqMusicKey: String = ""
        private set

    /** 从磁盘加载（在 [top.fpsmaster.web.api.MusicRoutes] 构造时调用一次）。 */
    fun load() {
        if (!file.exists() || !file.isRegularFile()) {
            logger.debug("No music auth file at $file")
            return
        }
        runCatching {
            val json = file.reader().use { gson.fromJson(it, JsonObject::class.java) } ?: return
            neteaseCookie = json.get("neteaseCookie")?.takeIf { !it.isJsonNull }?.asString ?: ""
            qqMusicId = json.get("qqMusicId")?.takeIf { !it.isJsonNull }?.asString ?: ""
            qqMusicKey = json.get("qqMusicKey")?.takeIf { !it.isJsonNull }?.asString ?: ""
            logger.debug("Loaded music auth from $file")
        }.onFailure { logger.error("Failed to load music auth file", it) }
    }

    fun setNetease(cookie: String) { neteaseCookie = cookie; save() }
    fun clearNetease() { neteaseCookie = ""; save() }

    fun setQq(musicId: String, musicKey: String) { qqMusicId = musicId; qqMusicKey = musicKey; save() }
    fun clearQq() { qqMusicId = ""; qqMusicKey = ""; save() }

    private fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            val json = JsonObject().apply {
                addProperty("neteaseCookie", neteaseCookie)
                addProperty("qqMusicId", qqMusicId)
                addProperty("qqMusicKey", qqMusicKey)
                addProperty("lastUpdated", System.currentTimeMillis())
            }
            file.writer().use { gson.toJson(json, it) }
        }.onFailure { logger.error("Failed to save music auth file", it) }
    }

    private fun resolveFile(): Path {
        val appData = System.getenv("APPDATA")
        val dir = if (!appData.isNullOrBlank()) Paths.get(appData, "FPSMaster")
                  else Paths.get(System.getProperty("user.home"), ".fpsmaster")
        return dir.resolve(FILE_NAME)
    }
}
