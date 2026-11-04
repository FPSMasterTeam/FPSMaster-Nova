package top.fpsmaster.account

import java.nio.file.Path
import java.nio.file.Paths

/**
 * The per-user FPSMaster directory: %APPDATA%/FPSMaster on Windows, ~/.fpsmaster elsewhere.
 * Credentials live here rather than in the game folder, which players zip up and share together
 * with their configs. Callers create the directory when they write.
 */
object UserDataDir {
    fun path(): Path {
        val appData = System.getenv("APPDATA")
        return if (!appData.isNullOrBlank()) {
            Paths.get(appData, "FPSMaster")
        } else {
            Paths.get(System.getProperty("user.home"), ".fpsmaster")
        }
    }

    fun file(name: String): Path = path().resolve(name)
}
