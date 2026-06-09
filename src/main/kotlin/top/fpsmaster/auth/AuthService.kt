package top.fpsmaster.auth

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

object AuthService {
    private const val AUTH_FILE_NAME = "auth.json"
    private const val SYSTEM_PROPERTY_TOKEN = "fpsmaster.auth.token"
    private const val SYSTEM_PROPERTY_REFRESH_TOKEN = "fpsmaster.auth.refreshToken"
    private const val SYSTEM_PROPERTY_TOKEN_EXPIRES_AT = "fpsmaster.auth.tokenExpiresAt"
    private const val DEFAULT_EXPIRES_IN_MS = 7L * 24L * 60L * 60L * 1000L

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val authFile = resolveAuthFile()

    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set
    var tokenExpiresAt: Long = 0L
        private set

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

    private fun load() {
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

    private fun save() {
        runCatching {
            Files.createDirectories(authFile.parent)
            val json = JsonObject()
            accessToken?.let { json.addProperty("accessToken", it) }
            refreshToken?.let { json.addProperty("refreshToken", it) }
            json.addProperty("tokenExpiresAt", tokenExpiresAt)
            json.addProperty("lastUpdated", System.currentTimeMillis())

            authFile.writer().use { writer ->
                gson.toJson(json, writer)
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
