package top.fpsmaster.auth

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthServiceLaunchTokenTest {
    private val keys = listOf("fpsmaster.auth.token", "fpsmaster.auth.refreshToken", "fpsmaster.auth.tokenExpiresAt")

    @AfterTest
    fun cleanUp() {
        keys.forEach { System.clearProperty(it) }
    }

    @Test
    fun `environment variable wins and the properties are cleared`() {
        System.setProperty("fpsmaster.auth.token", "from-property")
        System.setProperty("fpsmaster.auth.refreshToken", "refresh")

        val launch = AuthService.takeLauncherCredentials("from-env")

        assertEquals("from-env", launch?.token)
        assertEquals("refresh", launch?.refreshToken)
        keys.forEach { assertNull(System.getProperty(it), "$it must not stay readable") }
    }

    @Test
    fun `falls back to the legacy property`() {
        System.setProperty("fpsmaster.auth.token", "from-property")

        assertEquals("from-property", AuthService.takeLauncherCredentials(null)?.token)
        assertNull(System.getProperty("fpsmaster.auth.token"))
    }

    @Test
    fun `blank values mean no launcher token`() {
        System.setProperty("fpsmaster.auth.token", " ")
        assertNull(AuthService.takeLauncherCredentials(""))
    }
}
