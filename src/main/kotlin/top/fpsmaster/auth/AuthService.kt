package top.fpsmaster.auth

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import top.fpsmaster.logger
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader
import kotlin.io.path.writer

object AuthService {
    private const val AUTH_FILE_NAME = "auth.json"
    private const val SYSTEM_PROPERTY_TOKEN = "fpsmaster.auth.token"
    private const val SYSTEM_PROPERTY_REFRESH_TOKEN = "fpsmaster.auth.refreshToken"
    private const val SYSTEM_PROPERTY_TOKEN_EXPIRES_AT = "fpsmaster.auth.tokenExpiresAt"
    private const val DEFAULT_EXPIRES_IN_MS = 7L * 24L * 60L * 60L * 1000L

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val authFile = resolveAuthFile()

    /**
     * 渲染线程每帧经 [isLoggedIn] 读这三个字段，而写它们的线程有三条（登录、登出、
     * profile 刷新回调），所以必须 @Volatile——否则界面可能长期读到旧值、显示成已登录。
     */
    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    @Volatile
    var tokenExpiresAt: Long = 0L
        private set

    /** 串行化对 [authFile] 的读写：两条线程并发写会在文件里留下半截 JSON。 */
    private val fileLock = Any()

    fun initialize() {
        val launcherToken = System.getProperty(SYSTEM_PROPERTY_TOKEN)
        if (!launcherToken.isNullOrBlank()) {
            accessToken = launcherToken
            refreshToken = System.getProperty(SYSTEM_PROPERTY_REFRESH_TOKEN)
            tokenExpiresAt = parseExpiresAt(System.getProperty(SYSTEM_PROPERTY_TOKEN_EXPIRES_AT))
            save()
            logger.info("Loaded FPSMaster auth token from launcher")
            return
        }

        load()
    }

    fun isLoggedIn(): Boolean {
        return !accessToken.isNullOrBlank() && !isTokenExpired()
    }

    fun isTokenExpired(): Boolean {
        return tokenExpiresAt > 0L && System.currentTimeMillis() >= tokenExpiresAt
    }

    fun saveTokens(accessToken: String, refreshToken: String?, expiresAt: Long = defaultExpiresAt()) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.tokenExpiresAt = expiresAt
        save()
    }

    fun clearTokens() {
        accessToken = null
        refreshToken = null
        tokenExpiresAt = 0L
        save()
    }

    fun authFilePath(): Path {
        return authFile
    }

    private fun load(): Unit = synchronized(fileLock) {
        if (!authFile.exists() || !authFile.isRegularFile()) {
            logger.debug("No FPSMaster auth file found at $authFile")
            return
        }

        runCatching {
            val json = authFile.reader().use { gson.fromJson(it, JsonObject::class.java) } ?: return
            accessToken = json.stringOrNull("accessToken")
            refreshToken = json.stringOrNull("refreshToken")
            tokenExpiresAt = json.longOrZero("tokenExpiresAt")
            logger.debug("Loaded FPSMaster auth file from $authFile")
        }.onFailure { exception ->
            logger.error("Failed to load FPSMaster auth file", exception)
        }
    }

    private fun save(): Unit = synchronized(fileLock) {
        runCatching {
            Files.createDirectories(authFile.parent)
            val json = JsonObject()
            accessToken?.let { json.addProperty("accessToken", it) }
            refreshToken?.let { json.addProperty("refreshToken", it) }
            json.addProperty("tokenExpiresAt", tokenExpiresAt)
            json.addProperty("lastUpdated", System.currentTimeMillis())

            // 先写临时文件再整体换名。直接往 auth.json 上写的话，写到一半崩溃/断电
            // 会留下一个截断的 JSON，下次启动解析失败＝凭据凭空消失，玩家得重新登录。
            //
            // 临时文件名必须带进程号：Edge 和 Nova 落在同一个 auth.json 上（两边
            // resolveAuthFile() 算出的是同一条路径），fileLock 只锁得住本进程。同时开着
            // 两个客户端时，固定名字的 auth.json.tmp 会被两边同时写，先改名的那个搬走的
            // 可能是另一边写了一半的正文——原子改名保住的只是「换名这一步」不撕裂，
            // 保不住内容。
            val temp = authFile.resolveSibling("$AUTH_FILE_NAME.${ProcessHandle.current().pid()}.tmp")
            try {
                temp.writer().use { writer ->
                    gson.toJson(json, writer)
                }
                try {
                    Files.move(
                        temp,
                        authFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (unsupported: AtomicMoveNotSupportedException) {
                    // 某些文件系统（部分网络盘）不支持原子改名，退化成普通替换。
                    Files.move(temp, authFile, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (exception: Exception) {
                // 写失败/改名失败都会把半截临时文件留在目录里，下次 save 又写一个新的。
                Files.deleteIfExists(temp)
                throw exception
            }
        }.onFailure { exception ->
            logger.error("Failed to save FPSMaster auth file", exception)
        }
    }

    private fun resolveAuthFile(): Path {
        val appData = System.getenv("APPDATA")
        val directory = if (!appData.isNullOrBlank()) {
            Paths.get(appData, "FPSMaster")
        } else {
            Paths.get(System.getProperty("user.home"), ".fpsmaster")
        }
        return directory.resolve(AUTH_FILE_NAME)
    }

    private fun parseExpiresAt(value: String?): Long {
        return value?.toLongOrNull() ?: defaultExpiresAt()
    }

    private fun defaultExpiresAt(): Long {
        return System.currentTimeMillis() + DEFAULT_EXPIRES_IN_MS
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val element = get(name)
        return if (element == null || element.isJsonNull) null else element.asString
    }

    private fun JsonObject.longOrZero(name: String): Long {
        val element = get(name)
        return if (element == null || element.isJsonNull) 0L else element.asLong
    }
}
