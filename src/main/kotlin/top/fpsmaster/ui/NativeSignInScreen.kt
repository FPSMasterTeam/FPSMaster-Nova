package top.fpsmaster.ui

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.network.chat.Component
import top.fpsmaster.auth.ApiResult
import top.fpsmaster.auth.AuthService
import top.fpsmaster.auth.FPSMasterApiClient
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.prism.screen.SharedSignIn
import top.fpsmaster.prism.screen.SignInBridge
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.screenCompat
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen
import java.awt.Desktop
import java.net.URI

/**
 * FPSMaster 产品账号登录（不是 Minecraft 账号，那条在首页的账号胶囊里）。
 *
 * 入口有两处：首页账号浮层的「FPSMaster 账号」一行，以及饰品界面未登录时的购买按钮。
 */
class NativeSignInScreen(
    private val parent: net.minecraft.client.gui.screens.Screen?
) : ToolkitScreen(Component.literal("FPSMaster Sign In")) {
    private companion object {
        /** 和 Edge 的 OOBE 登录页指的是同一个网页入口。 */
        const val WEBSITE_URL = "https://fpsmaster.top/login"

        /** 后端 `banReason` 为空时的兜底原话（AuthService.loginInternal / CurrentUser）。 */
        const val BACKEND_DEFAULT_BAN_REASON = "account is banned"
    }

    private val gui = SharedSignIn()
    private val bridge = NovaSignInBridge()

    /**
     * 请求在网络线程上跑完再回到主线程写这些字段，所以都得是 @Volatile：渲染线程每帧都读。
     */
    @Volatile
    private var busy = false

    @Volatile
    private var error = ""

    /** 同一个界面里可能连着登录好几次，用它把迟到的响应丢掉。 */
    private var attempt = 0

    /** `init()` 在 resize 时也会跑，这时候不能清掉玩家已经输了一半的账号。 */
    private var initialised = false

    override fun init() {
        super.init()
        if (!initialised) {
            gui.reset()
            initialised = true
        }
        // token 还有效但本次会话没登录过时，缓存是空的，名字会显示成「未知账号」。
        // 这里异步补一次；渲染线程只读缓存，绝不在 paint 里发请求。
        FPSMasterApiClient.refreshUserInfoAsync()
    }

    override fun renderToolkitBackground(guiGraphics: GuiGraphics, partialTick: Float) {
        MainMenuBackgroundRenderer.render(guiGraphics, width, height, partialTick)
    }

    override fun renderUi(ui: UiFrame) {
        gui.paint(ui, bridge)
    }

    override fun handleEscape(): Boolean {
        // ToolkitScreen 先把按键喂给 frameInput 再走到这里，但 SharedSignIn 的
        // consumeKey(ESCAPE) 实际上永远拿不到——DeferredUi.drain() 在 runTick HEAD 就跑了、
        // removed() 又会 endFrame()。所以关闭只由这里负责。
        back()
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    private fun back() {
        // 迟到的登录响应可能在玩家已经翻到别的界面之后才回来，这时候不能把人拽回去。
        if (mc.screenCompat !== this) {
            return
        }
        // setScreenCompat 自己会在 DeferredUi.painting() 时延迟，不用再包一层。
        mc.setScreenCompat(parent)
    }

    private inner class NovaSignInBridge : SignInBridge {
        override fun i18n(key: String): String = Language.get(key)

        override fun signedIn(): Boolean = AuthService.isLoggedIn()

        override fun accountName(): String {
            val user = FPSMasterApiClient.cachedUser() ?: return ""
            return user.username ?: user.displayName ?: user.email ?: ""
        }

        override fun busy(): Boolean = busy

        override fun error(): String = error

        override fun submit(account: String, password: String) {
            if (busy) {
                return
            }
            busy = true
            error = ""
            val generation = ++attempt
            // login() 的请求构造是同步跑在这条（渲染）线程上的：token/URI 非法时 header()
            // 会当场抛，而这里是 prism 的按钮回调，异常漏出去就是一份 crash report；
            // 就算不崩，busy 也会永远卡在 true，登录按钮再也点不动。
            val pending = try {
                FPSMasterApiClient.login(account, password)
            } catch (exception: Exception) {
                logger.warn("FPSMaster sign-in request could not be started", exception)
                busy = false
                error = Language.get("signin.failed.network")
                return
            }
            pending.whenComplete { result, exception ->
                mc.execute {
                    if (generation != attempt) {
                        return@execute
                    }
                    busy = false
                    when {
                        exception != null -> {
                            logger.warn("FPSMaster sign-in request failed", exception)
                            error = Language.get("signin.failed.network")
                        }
                        result.success -> {
                            error = ""
                            // 登录成功就直接回到来处，省一次点击。
                            back()
                        }
                        else -> error = localizeLoginError(result)
                    }
                }
            }
        }

        /**
         * 后端和 [FPSMasterApiClient.parseResponse] 给的都是裸英文（`invalid credentials`、
         * `HTTP 401`、`Empty response (500)`），直接贴到界面上中文玩家看不懂，
         * 所以按状态码分流到 lang 文件里的文案。
         */
        private fun localizeLoginError(result: ApiResult<*>): String = when {
            result.statusCode == 0 -> Language.get("signin.failed.network")
            result.statusCode == 401 -> Language.get("signin.failed.credentials")
            // 后端对被封账号返回 403，message 就是封禁原因（AuthService.loginInternal）。
            // 并进 401 的话玩家看到的是「账号或密码不正确」，会反复改密码而不是来申诉。
            result.statusCode == 403 -> banMessage(result)
            result.statusCode == 429 -> Language.get("signin.failed.throttled")
            result.statusCode >= 500 -> Language.get("signin.failed.server")
            else -> result.message.ifBlank { Language.get("signin.failed") }
        }

        /**
         * 403 的文案。
         *
         * 只有后端按契约给出的原话才直接显示：Cloudflare 之类挡在前面时正文是 HTML，
         * 解析失败后 message 会是 `Parse error: ...`，贴上去就是一句英文技术黑话。后端自己
         * 那句兜底的 `account is banned`（`banReason` 为空时用）也要换成中文，
         * 否则这个 key 等于白加。
         */
        private fun banMessage(result: ApiResult<*>): String {
            val reason = result.message
            if (!result.serverMessage || reason.isBlank() || reason == BACKEND_DEFAULT_BAN_REASON) {
                return Language.get("signin.failed.banned")
            }
            return reason
        }

        override fun signOut() {
            if (busy) {
                return
            }
            busy = true
            error = ""
            val generation = ++attempt
            FPSMasterApiClient.logout().whenComplete { result, exception ->
                mc.execute {
                    if (generation != attempt) {
                        return@execute
                    }
                    busy = false
                    // `logout()` 自己 handle 过异常，所以 exception 基本到不了这儿；
                    // 到了也只是本地 token 已清，界面照样回到未登录态。
                    if (exception != null) {
                        logger.warn("FPSMaster sign-out request failed", exception)
                    } else if (!result.success) {
                        logger.info("FPSMaster sign-out response: {}", result.message)
                    }
                    gui.reset()
                }
            }
        }

        override fun close() {
            back()
        }

        override fun canOpenWebsite(): Boolean = Desktop.isDesktopSupported()

        override fun openWebsite() {
            runCatching {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI(WEBSITE_URL))
                }
            }.onFailure { exception ->
                logger.warn("Failed to open the FPSMaster website", exception)
            }
        }
    }
}
