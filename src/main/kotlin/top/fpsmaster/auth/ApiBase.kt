package top.fpsmaster.auth

/**
 * Single resolution point for the FPSMaster backend address.
 *
 * Order: system property `fpsmaster.api.baseUrl`, then environment variable `FPSMASTER_API_BASE_URL`,
 * then the production default. Every relative API path in the client goes through [v1] or [absolute]
 * so a local backend (`-Dfpsmaster.api.baseUrl=http://127.0.0.1:8722`) redirects auth, catalog,
 * purchases, loadout and cosmetic assets together.
 */
object ApiBase {
    private const val PRODUCTION = "https://api.fpsmaster.top"
    private const val VERSION_PATH = "/api/v1"

    private val base: String = resolve()

    /** Backend origin without a trailing slash. */
    @JvmStatic
    fun url(): String = base

    /** Absolute URL for a `/api/v1` path, e.g. `v1("/me/cosmetics/loadout")`. */
    @JvmStatic
    fun v1(path: String): String = base + VERSION_PATH + path

    /** Absolute URL for a server-relative path (cosmetic asset keys); absolute inputs pass through. */
    @JvmStatic
    fun absolute(pathOrUrl: String): String = if (pathOrUrl.startsWith('/')) base + pathOrUrl else pathOrUrl

    private fun resolve(): String {
        val configured = System.getProperty("fpsmaster.api.baseUrl")?.takeIf { it.isNotBlank() }
            ?: System.getenv("FPSMASTER_API_BASE_URL")?.takeIf { it.isNotBlank() }
            ?: PRODUCTION
        return configured.trim().trimEnd('/')
    }
}
