package top.fpsmaster.ui

import net.fabricmc.loader.api.FabricLoader
//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.network.chat.Component
import top.fpsmaster.Client
import top.fpsmaster.account.AccountManager
import top.fpsmaster.account.MicrosoftAuth
import top.fpsmaster.mc
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen
import top.fpsmaster.prism.screen.MenuBridge
import top.fpsmaster.prism.screen.SharedAccountOverlay
import top.fpsmaster.prism.screen.SharedMainMenu
import top.fpsmaster.prism.theme.Argb
import top.fpsmaster.prism.widget.UiFrame
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class NativeMainMenuScreen : ToolkitScreen(Component.literal("FPSMaster")) {
    private val overlay = SharedAccountOverlay()
    private val bridge = NovaMenuBridge()
    private val ms = MicrosoftLoginState()

    override fun renderToolkitBackground(guiGraphics: GuiGraphics, partialTick: Float) {
        MainMenuBackgroundRenderer.render(guiGraphics, width, height, partialTick)
    }

    override fun renderUi(ui: UiFrame) {
        SharedMainMenu.draw(ui, bridge)
        overlay.draw(ui, bridge)
    }

    override fun shouldCloseOnEsc(): Boolean = false

    private inner class NovaMenuBridge : MenuBridge {
        override fun i18n(key: String): String = Language.get(key)
        override fun edition(): String = "NOVA"
        override fun version(): String = Client.VERSION
        override fun minecraftLabel(): String {
            val v = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map { it.metadata.version.friendlyString }
                .orElse("?")
            return "Minecraft $v"
        }

        override fun playerName(): String = AccountManager.get().currentName()
        override fun accountTypeLabel(): String =
            if (AccountManager.get().currentOnline()) Language.get("mainmenu.account.ms")
            else Language.get("mainmenu.account.offline")

        override fun continueServer(): MenuBridge.ContinueServer? = loadContinue()

        override fun showReplays(): Boolean = false
        override fun showDevtools(): Boolean = FabricLoader.getInstance().isDevelopmentEnvironment
        override fun interactive(): Boolean = !overlay.blocking()
        override fun accountOpen(): Boolean = overlay.popOpen() || overlay.blocking()
        override fun account() {
            overlay.togglePop()
        }

        override fun drawAvatar(ui: UiFrame, x: Float, y: Float, size: Float) {
            val name = playerName()
            val hue = (Math.abs(name.hashCode()) % 360) / 360f
            ui.canvas().fillRoundRect(x, y, size, size, 4f, java.awt.Color.HSBtoRGB(hue, 0.45f, 0.75f))
            val letter = if (name.isEmpty()) "?" else name.substring(0, 1).uppercase()
            val font = ui.font(11)
            ui.canvas().drawString(font, letter, x + (size - font.measure(letter)) / 2f, y + 3f, Argb.rgb(255, 255, 255))
        }

        override fun accounts(): List<MenuBridge.AccountRow> {
            val manager = AccountManager.get()
            val rows = ArrayList<MenuBridge.AccountRow>()
            manager.launcherAccount()?.let { launcher ->
                rows.add(
                    MenuBridge.AccountRow(
                        AccountManager.LAUNCHER_ID,
                        launcher.name,
                        if (launcher.microsoft()) Language.get("mainmenu.account.ms")
                        else Language.get("mainmenu.account.offline"),
                        manager.isLauncherCurrent(),
                        false,
                        launcher.microsoft()
                    )
                )
            }
            for (account in manager.storedAccounts()) {
                val launcher = manager.launcherAccount()
                if (launcher != null && account.name.equals(launcher.name, true) && !account.microsoft()) {
                    continue
                }
                rows.add(
                    MenuBridge.AccountRow(
                        account.name,
                        account.name,
                        if (account.microsoft()) Language.get("mainmenu.account.ms")
                        else Language.get("mainmenu.account.offline"),
                        account.name.equals(manager.currentName()),
                        true,
                        account.microsoft()
                    )
                )
            }
            return rows
        }

        override fun selectAccount(id: String) {
            AccountManager.get().selectById(id)
        }

        override fun removeAccount(id: String) {
            AccountManager.get().removeById(id)
        }

        override fun startMicrosoftLogin() {
            overlay.openMicrosoft()
            ms.start()
        }

        override fun addOffline(name: String): Boolean = AccountManager.get().addAndUse(name)

        override fun openMicrosoftUrl() {
            ms.openUrl()
        }

        override fun copyMicrosoftCode() {
            val code = ms.userCode
            if (code.isNotEmpty()) {
                mc.keyboardHandler.clipboard = code
            }
        }

        override fun retryMicrosoftLogin() {
            ms.start()
        }

        override fun cancelMicrosoftLogin() {
            ms.abort()
        }

        override fun microsoftCode(): String = ms.userCode
        override fun microsoftStatus(): String = ms.status
        override fun microsoftError(): String = ms.error
        override fun microsoftBusy(): Boolean = ms.busy
        override fun microsoftHasUrl(): Boolean = ms.verifyUrl.isNotEmpty()

        override fun singleplayer() {
            mc.setScreenCompat(SelectWorldScreen(this@NativeMainMenuScreen))
        }

        override fun multiplayer() {
            mc.setScreenCompat(JoinMultiplayerScreen(this@NativeMainMenuScreen))
        }

        override fun settings() {
            //? if >=26 {
            /*mc.setScreenCompat(net.minecraft.client.gui.screens.options.OptionsScreen(this@NativeMainMenuScreen, mc.options, false))*/
            //?}
            //? if >=1.20.5 && <26 {
            mc.setScreenCompat(net.minecraft.client.gui.screens.options.OptionsScreen(this@NativeMainMenuScreen, mc.options))
            //?}
            //? if <1.20.5 {
            /*mc.setScreenCompat(net.minecraft.client.gui.screens.OptionsScreen(this@NativeMainMenuScreen, mc.options))*/
            //?}
        }

        override fun replays() {}

        override fun music() {
            mc.setScreenCompat(NativeMusicScreen(this@NativeMainMenuScreen))
        }

        override fun backgrounds() {
            mc.setScreenCompat(NativeBackgroundScreen(this@NativeMainMenuScreen))
        }

        override fun quit() {
            mc.stop()
        }

        override fun continueConnect() {
            multiplayer()
        }

        override fun devtools() {
            Client.openClickGui()
        }
    }

    private fun loadContinue(): MenuBridge.ContinueServer? {
        return try {
            val list = net.minecraft.client.multiplayer.ServerList(mc)
            list.load()
            if (list.size() <= 0) {
                return null
            }
            val server = list.get(0)
            val name = server.name.takeIf { it.isNotBlank() } ?: server.ip
            MenuBridge.ContinueServer(name, server.ip, server.ping)
        } catch (_: Exception) {
            null
        }
    }

    private inner class MicrosoftLoginState {
        @Volatile var busy = false
        @Volatile var userCode = ""
        @Volatile var verifyUrl = ""
        @Volatile var status = ""
        @Volatile var error = ""
        @Volatile var finished = false
        private val cancelled = AtomicBoolean(false)
        private val generation = AtomicInteger(0)
        @Volatile private var session: MicrosoftAuth.BrowserSession? = null

        fun abort() {
            cancelled.set(true)
            busy = false
            session?.close()
        }

        fun start() {
            cancelled.set(false)
            busy = true
            userCode = ""
            verifyUrl = ""
            status = Language.get("mainmenu.account.ms.starting")
            error = ""
            finished = false
            session?.close()
            session = null
            val gen = generation.incrementAndGet()
            thread(name = "FPSMaster-MS-Login", isDaemon = true) {
                runLogin(gen)
            }
        }

        fun openUrl() {
            val url = verifyUrl
            if (url.isBlank()) {
                return
            }
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI(url))
                }
            } catch (_: Exception) {
            }
        }

        private fun runLogin(gen: Int) {
            try {
                val browser = MicrosoftAuth.beginBrowserLogin()
                if (cancelled(gen)) {
                    browser.close()
                    return
                }
                session = browser
                verifyUrl = browser.authorizeUrl
                status = Language.get("mainmenu.account.ms.waiting")
                busy = false
                openUrl()
                val profile = browser.await { cancelled(gen) }
                if (cancelled(gen)) {
                    return
                }
                mc.execute {
                    AccountManager.get().addAndUseMicrosoft(profile)
                    finished = true
                    overlay.closeDialog()
                }
            } catch (exception: Exception) {
                if (cancelled(gen) || exception.message == "cancelled") {
                    return
                }
                error = localize(exception.message)
                status = ""
            } finally {
                busy = false
                session = null
            }
        }

        private fun cancelled(gen: Int): Boolean {
            return cancelled.get() || gen != generation.get()
        }

        private fun localize(message: String?): String {
            val raw = message?.trim().orEmpty()
            return when {
                raw == "NO_JAVA_LICENSE" -> Language.get("mainmenu.account.ms.nolicense")
                raw == "NO_JAVA_PROFILE" -> Language.get("mainmenu.account.ms.noprofile")
                raw.contains("access_denied", ignoreCase = true) -> Language.get("mainmenu.account.ms.denied")
                raw.isEmpty() -> Language.get("mainmenu.account.ms.failed").replace("%s", "unknown")
                else -> {
                    val short = raw.substringBefore(". Trace").substringBefore(" Trace ID")
                    val shown = if (short.length > 96) short.take(96) + "…" else short
                    Language.get("mainmenu.account.ms.failed").replace("%s", shown)
                }
            }
        }
    }
}
