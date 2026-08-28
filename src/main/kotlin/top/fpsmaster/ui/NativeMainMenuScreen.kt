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
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import top.fpsmaster.Client
import top.fpsmaster.account.AccountManager
import top.fpsmaster.account.MicrosoftAuth
import top.fpsmaster.logger
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

    /**
     * `SharedMainMenu.draw` 每帧都要问一次 `continueServer()`，而读服务器列表是一次磁盘 +
     * NBT 解析，不能挂在渲染路径上（Edge 也是在 `initGui` 里读的）。
     */
    private var continueServerData: ServerData? = null

    /**
     * `init()` 不只在 setScreen 时跑：`Screen.resize` 也会经 `repositionElements` →
     * `rebuildWidgets` 绕回 `init()`，而 macOS 拖窗口边缘是连续触发的。所以用「离屏时置脏」
     * 而不是「每次 init 都读」，正好是一次显示读一遍、resize 零读。
     */
    private var continueDirty = true

    override fun init() {
        super.init()
        if (continueDirty) {
            continueServerData = readFirstServer()
            continueDirty = false
        }
        // token 还有效但本次会话没登录过时，账号浮层会显示成「未知账号」。
        // 这里异步补一次缓存；fpsAccountName() 每帧都被调用，必须保持纯读。
        top.fpsmaster.auth.FPSMasterApiClient.refreshUserInfoAsync()
    }

    override fun removed() {
        super.removed()
        // 从多人列表/连接界面退回来时重读，顺带解决「在列表里加了台服务器、退回来 Continue
        // 格子还是旧的」。
        continueDirty = true
    }

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
        // Nova 首页不出「开发工具」格子：Edge 那边它挂的是一个真正的 DevToolsScreen，
        // Nova 只能把它接到 ClickGUI 上，语义对不上，索性对齐成不显示。
        override fun showDevtools(): Boolean = false
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

        override fun showFpsAccount(): Boolean = true

        override fun fpsSignedIn(): Boolean = top.fpsmaster.auth.AuthService.isLoggedIn()

        override fun fpsAccountName(): String {
            val user = top.fpsmaster.auth.FPSMasterApiClient.cachedUser() ?: return ""
            return user.username ?: user.displayName ?: user.email ?: ""
        }

        override fun openFpsSignIn() {
            mc.setScreenCompat(NativeSignInScreen(this@NativeMainMenuScreen))
        }

        override fun music() {
            mc.setScreenCompat(NativeMusicScreen(this@NativeMainMenuScreen))
        }

        override fun backgrounds() {
            mc.setScreenCompat(NativeBackgroundScreen(this@NativeMainMenuScreen))
        }

        override fun quit() {
            // 同样不能在 render 栈里跑：Minecraft.stop 会拆掉窗口和渲染资源。
            top.fpsmaster.ui.kit.DeferredUi.defer(Runnable { mc.stop() })
        }

        override fun continueConnect() {
            // 格子上写的是具体某台服务器，就得直接连过去，而不是把多人列表丢给玩家（Edge 走的是
            // GuiConnecting）。
            val server = continueServerData ?: return
            // 跟 quit() 一样必须出了 render 栈再做：startConnecting 内部会
            // disconnect/clearLevel → forceSetScreen → runTick，等于在 GameRenderer.render
            // 里再嵌套跑一帧。别处的切屏靠 setScreenCompat 兜底，而这是个 vanilla 静态方法，
            // 绕过了那层保护，只能自己排队。详见 DeferredUi 的注释。
            top.fpsmaster.ui.kit.DeferredUi.defer(Runnable {
                // `startConnecting` 的形参在三代之间加过东西，所以按代分支。
                // 和 loadContinue() 保持同一套姿态：`ServerData.ip` 是平台类型。
                val address = ServerAddress.parseString(server.ip.orEmpty())
                //? if >=1.21 {
                ConnectScreen.startConnecting(this@NativeMainMenuScreen, mc, address, server, false, null)
                //?}
                //? if >=1.20 && <1.21 {
                /*ConnectScreen.startConnecting(this@NativeMainMenuScreen, mc, address, server, false)*/
                //?}
                //? if <1.20 {
                /*ConnectScreen.startConnecting(this@NativeMainMenuScreen, mc, address, server)*/
                //?}
            })
        }

        override fun devtools() {
            // 当前不可达：`showDevtools()` 恒 false，`SharedMainMenu` 只在出格子时才派发。
            // 保留实现是为了「哪天把格子放回来」时行为仍然正确，而不是留一个空壳。
            Client.openClickGui()
        }
    }

    private fun readFirstServer(): ServerData? {
        return try {
            val list = ServerList(mc)
            list.load()
            if (list.size() > 0) list.get(0) else null
        } catch (exception: Exception) {
            // `ServerList.load()` 自己已经把整段包在 try/catch 里（坏档只会得到空列表，不会抛），
            // 这层只兜构造与其它意外，真触发了就该有一行日志而不是静默变「没有服务器」。
            logger.warn("Failed to read the server list for the continue card", exception)
            null
        }
    }

    private fun loadContinue(): MenuBridge.ContinueServer? {
        val server = continueServerData ?: return null
        // `ServerData.name` / `.ip` 在各代都是无注解的 Java 字段（平台类型），Kotlin 不会替我们
        // 检查；这里显式兜一层，免得万一为 null 时 `isNotBlank()` 在渲染循环里抛出去。
        val ip = server.ip.orEmpty()
        val name = server.name?.takeIf { it.isNotBlank() } ?: ip
        return MenuBridge.ContinueServer(name, ip, server.ping)
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

        /** 串行化「关旧 socket → 绑新端口 → 发布会话」，见 [openSession]。 */
        private val sessionLock = Any()

        fun abort() {
            cancelled.set(true)
            // 也推进代际：`cancelled` 会被下一次 start() 置回 false，只靠它的话
            // 被取消的那个 worker 在下一轮开始后又会把自己当成当前一代（Edge 侧的
            // cancelMicrosoftLogin 就是标志位 + 代际两件一起做）。
            generation.incrementAndGet()
            busy = false
            // 必须连字段一起清掉。留着的话下一次 start() 会对同一个已关闭的 socket
            // 再 close 一次，而真正需要判断「上一轮还占着端口吗」的地方就得不到答案了。
            closeSession()
        }

        private fun closeSession() = synchronized(sessionLock) {
            closeSessionLocked()
        }

        private fun closeSessionLocked() {
            val current = session
            session = null
            current?.close()
        }

        fun start() {
            // 代际号必须先推进：旧 worker 的 cancelled(gen) 靠它判断自己是不是过期的，
            // 而下面第一句就把 cancelled 清回 false，中间任何一点都不能让旧 worker
            // 误认为自己仍然当代。
            val gen = generation.incrementAndGet()
            cancelled.set(false)
            busy = true
            userCode = ""
            verifyUrl = ""
            status = Language.get("mainmenu.account.ms.starting")
            error = ""
            finished = false
            closeSession()
            thread(name = "FPSMaster-MS-Login", isDaemon = true) {
                runLogin(gen)
            }
        }

        fun openUrl(url: String = verifyUrl) {
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

        /**
         * 在锁内一次做完「关掉上一轮的 socket → 绑本轮的端口 → 发布会话状态」；
         * 本代已被取消时返回 null（并且不留下任何痕迹）。
         *
         * 分成几步做是不行的。连点两次登录时旧 worker 可能还占着 127.0.0.1:3389 却尚未把
         * session 发布出来，[start] 的 close 扑了个空、新 worker 的 bind 就撞上去：
         * Linux/macOS 报「端口被占用」这条完全误导的提示，Windows 的 SO_REUSEADDR 更糟——
         * 两个 listener 同时活着，回调可能落到已取消的那一个。
         * 发布状态同样要在锁里：check 完再裸写就是 TOCTOU，过期 worker 会把 verifyUrl
         * 覆盖成自己那条已死会话的地址，玩家点开浏览器后永远等不到回调。
         */
        private fun openSession(gen: Int): MicrosoftAuth.BrowserSession? = synchronized(sessionLock) {
            if (cancelled(gen)) {
                return@synchronized null
            }
            closeSessionLocked()
            val opened = MicrosoftAuth.beginBrowserLogin()
            if (cancelled(gen)) {
                opened.close()
                return@synchronized null
            }
            session = opened
            verifyUrl = opened.authorizeUrl
            status = Language.get("mainmenu.account.ms.waiting")
            busy = false
            opened
        }

        private fun runLogin(gen: Int) {
            try {
                val browser = openSession(gen) ?: return
                // openSession 出锁到这里之间玩家可能已经取消或又点了一次登录：不重查一遍
                // 就会平白弹出一个属于上一轮的授权页，玩家在里面登完也没人收回调。
                if (cancelled(gen)) {
                    return
                }
                // 打开浏览器是同步的、能耗上百毫秒，不能占着锁；传局部量而不是读字段，
                // 免得被后一轮改掉之后打开的是别人的授权地址。
                openUrl(browser.authorizeUrl)
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
                // 只清自己那一代的状态：旧 worker 收尾时如果无条件清空，会把新 worker
                // 刚写进 session 的 socket 抹掉，导致后续 cancel 关不到它、下一次 bind 撞端口。
                //
                // 判代和清理必须在同一把锁里：在锁外判完再清，中间插进来的 start() 已经
                // 推进了代际并在 openSession 里发布了新 socket，这一句照样会把它抹掉——
                // 正是上面那句注释要防的场景。顺带用 closeSessionLocked() 而不是裸置空，
                // 免得正常走完的那轮把 127.0.0.1 上的监听端口一直占到退出游戏。
                synchronized(sessionLock) {
                    if (gen == generation.get()) {
                        busy = false
                        closeSessionLocked()
                    }
                }
            }
        }

        private fun cancelled(gen: Int): Boolean {
            return cancelled.get() || gen != generation.get()
        }

        private fun localize(message: String?): String {
            val raw = message?.trim().orEmpty()
            return when {
                // 端口被占用是玩家自己能处理的（关掉远程桌面 / 设 FPSMASTER_MINECRAFT_REDIRECT_URL），
                // 所以这条必须完整可读，不能被下面的 96 字截断吃掉。
                raw.startsWith("PORT_IN_USE:") ->
                    Language.get("mainmenu.account.ms.portbusy")
                        .replace("%s", raw.removePrefix("PORT_IN_USE:"))
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
