package top.fpsmaster.web

import net.minecraft.client.Minecraft
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
//? if >=1.21.11 {
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
//?}
import net.minecraft.network.chat.Component
import net.ccbluex.liquidbounce.mcef.MCEFAccelerationSupport
import org.lwjgl.glfw.GLFW
import top.fpsmaster.Client
import top.fpsmaster.logger
import top.fpsmaster.module.impl.render.ClickGUI
import top.fpsmaster.module.impl.ui.BetterScreen
import top.fpsmaster.web.cef.ClientBrowser
import top.fpsmaster.web.network.NetworkManager
import top.fpsmaster.web.network.packets.GuiLoadAckPacket
import top.fpsmaster.web.network.packets.GuiLoadEventPacket
import java.net.InetSocketAddress
import java.net.Socket

open class BasicBrowser(private val mode: Mode = Mode.CLICKGUI) : Screen(Component.literal("Browser")) {
    private val ACK_TIMEOUT_MS = 5000L  // 5秒超时
    private var closingRequested = false
    private var closeAckReceived = false
    private var closeRequestedAt = 0L
    private var browser: ClientBrowser? = null
    private var openEventSent = false
    private var waitingForOpenAck = false
    private var openAckReceived = false
    private var openEventSentAt = 0L
    private var lastOpenSendAt = 0L
    private var openAckTimedOut = false

    // renderBackground signature across eras: 4-arg GuiGraphics (>=1.20.5), 1-arg GuiGraphics
    // (1.20-1.20.4), PoseStack (1.19.2, pre-GuiGraphics).
    //? if >=1.20.5 {
    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // The main menu replaces the vanilla title screen, so render the configured menu background here.
        if (mode == Mode.MAINMENU) {
            top.fpsmaster.ui.MainMenuBackgroundRenderer.render(guiGraphics, width, height, partialTick)
            return
        }
        // Background blur behind the webview is intentionally never used.
        if (BetterScreen.isActive() && !BetterScreen.background.getValue()) {
            return
        }
        renderTransparentBackground(guiGraphics)
    }
    //?}
    //? if >=1.20 && <1.20.5 {
    /*override fun renderBackground(guiGraphics: GuiGraphics) {
        if (BetterScreen.isActive() && !BetterScreen.background.getValue()) {
            return
        }
        super.renderBackground(guiGraphics)
    }*/
    //?}
    //? if <1.20 {
    /*override fun renderBackground(poseStack: com.mojang.blaze3d.vertex.PoseStack) {
        if (BetterScreen.isActive() && !BetterScreen.background.getValue()) {
            return
        }
        super.renderBackground(poseStack)
    }*/
    //?}

    /**
     * 非阻塞发送GUI加载事件
     */
    private fun trySendGuiLoadEvent() {
        if (openAckReceived || openAckTimedOut || NetworkManager.getConnectionCount() <= 0) {
            return
        }

        // Resend until the frontend acks. Switching views fully reloads the page (loadURL), so the first
        // send can hit the old, dying page before the new one has connected and registered its packet
        // handler — that lost open event previously left waitingForOpenAck stuck true forever (which then
        // swallowed the close ACK, forcing a double ESC). Re-broadcasting on an interval guarantees the
        // freshly loaded page receives it. updateOpenAckState() stops us after ACK_TIMEOUT_MS.
        val now = System.currentTimeMillis()
        if (openEventSent && now - lastOpenSendAt < OPEN_RESEND_INTERVAL_MS) {
            return
        }

        try {
            val eventPacket = GuiLoadEventPacket().apply {
                eventType = "open"
                timestamp = now
                extraData = mode.id
            }

            logger.info("Sending GUI load event: $eventPacket")
            NetworkManager.broadcastPacket(eventPacket)
            openEventSent = true
            waitingForOpenAck = true
            if (openEventSentAt == 0L) {
                openEventSentAt = now
            }
            lastOpenSendAt = now
            openAckTimedOut = false
        } catch (e: Exception) {
            logger.error("Failed to send GUI load event", e)
        }
    }

    private fun updateOpenAckState() {
        if (!waitingForOpenAck || openAckTimedOut) {
            return
        }

        if (System.currentTimeMillis() - openEventSentAt >= ACK_TIMEOUT_MS) {
            openAckTimedOut = true
            waitingForOpenAck = false
            logger.warn("GUI load ACK timeout after ${ACK_TIMEOUT_MS}ms")
        }
    }

    /**
     * 发送GUI关闭事件并等待ACK
     */
    private fun sendGuiCloseEvent(): Boolean {
        try {
            val eventPacket = GuiLoadEventPacket().apply {
                eventType = "close"
                timestamp = System.currentTimeMillis()
                extraData = mode.id
            }
            logger.info("Sending GUI close event: $eventPacket")
            NetworkManager.broadcastPacket(eventPacket)

            // 不阻塞等待ACK，由处理器设置标志并在渲染线程执行关闭
            return true
        } catch (e: Exception) {
            logger.error("Failed to send GUI close event", e)
            return false
        }
    }

    /**
     * 处理收到的ACK包
     */
    fun handleGuiLoadAck(ack: GuiLoadAckPacket) {
        // Close takes priority: once the user asked to close, ANY ack completes the close. Checked before
        // waitingForOpenAck so a stale/never-answered open wait (open event lost during a view-switch page
        // reload) can't consume the close ACK and force the user to press ESC twice.
        if (closingRequested) {
            closeAckReceived = true
            waitingForOpenAck = false
            logger.info("GUI close ACK flagged for render thread: $ack")
            return
        }

        if (waitingForOpenAck || !openAckReceived) {
            waitingForOpenAck = false
            openAckReceived = true
            openAckTimedOut = false
            logger.info("GUI load ACK completed: $ack")
            return
        }

        // 关闭ACK由前端在关闭动画完成后发送
        if (mode == Mode.OOBE && ack.message.startsWith("oobe:")) {
            finishOobe(ack.message.removePrefix("oobe:") == "settings")
        } else {
            logger.warn("Received unexpected GUI load ACK: $ack")
        }
    }

    fun initBrowser() {
        if (!Client.cefReady) {
            INSTANCE = this
            return
        }

        if (browser == null) {
            browser = obtainSharedBrowser(mode)
        }
        if (width > 0 && height > 0) {
            browser!!.resize(width, height)
        }
        INSTANCE = this
    }



    var loaded = false
    var ackReceived = false

    override fun init() {
        super.init()
        initBrowser()
        trySendGuiLoadEvent()
        ackReceived = true
        loaded = true
    }




    //? if >=1.21.11 {
    override fun resize(i: Int, j: Int) {
        super.resize(i, j)
        browser?.resize(width, height)
    }
    //?} else {
    /*override fun resize(minecraft: Minecraft, i: Int, j: Int) {
        super.resize(minecraft, i, j)
        browser?.resize(width, height)
    }*/
    //?}


    //? if >=1.20 {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    //?} else {
    /*override fun render(poseStack: com.mojang.blaze3d.vertex.PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val guiGraphics = GuiGraphics(poseStack)*/
    //?}
        trySendGuiLoadEvent()
        updateOpenAckState()

        // 在渲染线程处理关闭逻辑，避免跨线程调用setScreen
        if (closingRequested && !closeAckReceived &&
            System.currentTimeMillis() - closeRequestedAt >= CLOSE_ACK_TIMEOUT_MS) {
            // Close event or its ACK was lost (frontend still loading right after a view switch): close
            // anyway so a single ESC always works instead of leaving the GUI stuck open.
            logger.warn("GUI close ACK timeout after ${CLOSE_ACK_TIMEOUT_MS}ms, closing anyway")
            closeAckReceived = true
        }
        if (closingRequested && closeAckReceived) {
            closingRequested = false
            closeAckReceived = false
            Minecraft.getInstance().setScreen(if (mode == Mode.OOBE) TitleScreen() else null)
            return
        }
        //? if >=1.20 {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        //?} else {
        /*super.render(poseStack, mouseX, mouseY, partialTick)*/
        //?}

        if (!ackReceived) {
            return
        }

        val currentBrowser = browser
        if (currentBrowser == null) {
            renderCefUnavailable(guiGraphics)
            return
        }

        currentBrowser.render(guiGraphics, width, height)
    }

    private fun renderCefUnavailable(guiGraphics: GuiGraphics) {
        val title = "ClickGUI WebView 未启动"
        val detail = Client.cefFailureMessage ?: "CEF 正在初始化，请稍后重试"
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            title,
            width / 2,
            height / 2 - 12,
            0xFFFFFFFF.toInt()
        )
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            detail,
            width / 2,
            height / 2 + 6,
            0xFFAAAAAA.toInt()
        )
    }

    //? if >=1.21.11 {
    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        browser?.mouseClicked(event.x(), event.y(), event.button())
        return super.mouseClicked(event, isDoubleClick)
    }


    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        browser?.mouseReleased(event.x(), event.y(), event.button())
        return super.mouseReleased(event)
    }
    //?} else {
    /*override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        browser?.mouseClicked(mouseX, mouseY, button)
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        browser?.mouseReleased(mouseX, mouseY, button)
        return super.mouseReleased(mouseX, mouseY, button)
    }*/
    //?}


    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.sendMouseMove(mouseX, mouseY)
        super.mouseMoved(mouseX, mouseY)
    }

    // mouseDragged gained the MouseButtonEvent param in the 1.21.11 input rewrite...
    //? if >=1.21.11 {
    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        return super.mouseDragged(event, mouseX, mouseY)
    }
    //?} else {
    /*override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }*/
    //?}

    // ...but the 4-arg mouseScrolled (horizontal scrollX) predates it (1.20.2) — 1.21.1/1.21.8 have it.
    //? if >=1.20.5 {
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        browser?.sendMouseWheel(mouseX, mouseY, scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
    //?} else {
    /*override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        browser?.sendMouseWheel(mouseX, mouseY, scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollY)
    }*/
    //?}

    //? if >=1.21.11 {
    override fun keyPressed(event: KeyEvent): Boolean {
        // OOBE and the main menu are not dismissible with ESC (there is nothing behind them).
        if ((mode == Mode.OOBE || mode == Mode.MAINMENU) && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return false
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && !closingRequested) {
            closingRequested = true
            closeRequestedAt = System.currentTimeMillis()
            val ok = sendGuiCloseEvent()
            if (!ok) {
                closingRequested = false
            }
            return false
        }
        browser?.sendKeyPress(event.key(), event.scancode().toLong(), event.modifiers())
        return super.keyPressed(event)
    }


    override fun keyReleased(event: KeyEvent): Boolean {
        browser?.sendKeyRelease(event.key(), event.scancode().toLong(), event.modifiers())
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (event.codepoint() == 0.toChar().code) return false
        val s = event.codepointAsString()
        if (s.isEmpty()) return super.charTyped(event)
        // ASCII keeps the CEF char-event path (works fine); non-ASCII (IME-committed CJK, emoji, etc.)
        // is injected via JS because CEF KEY_TYPE doesn't reliably insert it on macOS OSR.
        if (s[0].code > 0x7e || s.length > 1) {
            browser?.insertText(s)
        } else {
            browser?.sendKeyTyped(s[0], event.modifiers())
        }
        return super.charTyped(event)
    }
    //?} else {
    /*override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if ((mode == Mode.OOBE || mode == Mode.MAINMENU) && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return false
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !closingRequested) {
            closingRequested = true
            closeRequestedAt = System.currentTimeMillis()
            val ok = sendGuiCloseEvent()
            if (!ok) {
                closingRequested = false
            }
            return false
        }
        browser?.sendKeyPress(keyCode, scanCode.toLong(), modifiers)
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        browser?.sendKeyRelease(keyCode, scanCode.toLong(), modifiers)
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (chr.code == 0) return false
        // ASCII keeps the CEF char-event path; non-ASCII (IME-committed CJK, etc.) is injected via JS
        // because CEF KEY_TYPE doesn't reliably insert it on macOS OSR.
        if (chr.code > 0x7e) {
            browser?.insertText(chr.toString())
        } else {
            browser?.sendKeyTyped(chr, modifiers)
        }
        return super.charTyped(chr, modifiers)
    }*/
    //?}

    override fun shouldCloseOnEsc(): Boolean = mode != Mode.OOBE && mode != Mode.MAINMENU

    override fun isPauseScreen(): Boolean = false

    private fun finishOobe(openSettings: Boolean) {
        closingRequested = false
        closeAckReceived = false
        Minecraft.getInstance().setScreen(if (openSettings) BasicBrowser(Mode.CLICKGUI) else TitleScreen())
    }

    companion object {
        private const val DEV_BROWSER_URL = "http://localhost:3000/"
        private const val PROD_BROWSER_URL = "http://localhost:7781/"
        private const val DEV_SERVER_TIMEOUT_MS = 200
        // Re-broadcast the open event this often (ms) until the frontend acks — covers the page reload on
        // view switches where the first send can miss the not-yet-ready new page.
        private const val OPEN_RESEND_INTERVAL_MS = 300L
        // Close anyway if no close ACK arrives within this long, so a single ESC always closes the GUI.
        private const val CLOSE_ACK_TIMEOUT_MS = 1500L
        private var sharedBrowser: ClientBrowser? = null
        private var prewarmAttempted = false
        private var resolvedBrowserUrl: String? = null
        private var lastReportedAcceleration: Boolean? = null
        private var lastReportedFrameRateLimit: Int? = null

        private fun currentGuiWidth(): Int {
            val window = Minecraft.getInstance().window
            return window.guiScaledWidth
        }

        private fun currentGuiHeight(): Int {
            val window = Minecraft.getInstance().window
            return window.guiScaledHeight
        }

        private fun resolveBrowserBaseUrl(): String {
            resolvedBrowserUrl?.let { return it }

            resolvedBrowserUrl = if (isDevServerAvailable()) {
                logger.info("Detected ClickGUI dev server on port 3000")
                DEV_BROWSER_URL
            } else {
                logger.info("ClickGUI dev server not detected, using bundled UI on port 7781")
                PROD_BROWSER_URL
            }
            return resolvedBrowserUrl!!
        }

        private fun browserUrl(mode: Mode): String {
            // Distinct path per mode (e.g. /clickgui, /mainmenu, /oobe) instead of a shared
            // ?view= query, so CEF treats each as a separate document and can't mix up caches.
            return "${resolveBrowserBaseUrl()}${mode.id}"
        }

        private fun isDevServerAvailable(): Boolean {
            return try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", 3000), DEV_SERVER_TIMEOUT_MS)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        private fun obtainSharedBrowser(mode: Mode = Mode.CLICKGUI): ClientBrowser {
            val targetUrl = browserUrl(mode)
            val frameRate = currentFrameRateLimit()
            if (sharedBrowser == null) {
                logger.info("Creating shared browser instance for $targetUrl")
                sharedBrowser = ClientBrowser(targetUrl, fps = frameRate, accelerate = shouldUseAcceleration())
            }

            val browser = sharedBrowser!!
            val acceleration = shouldUseAcceleration()
            if (browser.accelerate != acceleration || browser.fps != frameRate) {
                logger.info(
                    "Recreating browser for GPU acceleration enabled={}, frameRate={}",
                    acceleration,
                    frameRate
                )
                browser.close()
                sharedBrowser = ClientBrowser(targetUrl, fps = frameRate, accelerate = acceleration)
                val nextBrowser = sharedBrowser!!
                val guiWidth = currentGuiWidth()
                val guiHeight = currentGuiHeight()
                if (guiWidth > 0 && guiHeight > 0) {
                    nextBrowser.resize(guiWidth, guiHeight)
                }
                return nextBrowser
            }
            // Do NOT navigate the shared browser when switching views. The frontend is a single-page app
            // that switches view client-side from the GuiLoadEvent's extraData (mode id) — reloading the
            // page here would tear down the page + WebSocket and cause the stale-frame flash and the
            // open/close ACK races. The browser keeps the one document it loaded at creation.
            val guiWidth = currentGuiWidth()
            val guiHeight = currentGuiHeight()
            if (guiWidth > 0 && guiHeight > 0) {
                browser.resize(guiWidth, guiHeight)
            }
            return browser
        }

        private fun shouldUseAcceleration(): Boolean {
            val enabled = ClickGUI.hardwareAcceleration.getValue()
            val support = if (enabled) MCEFAccelerationSupport.getAccelerationSupport() else null
            val acceleration = enabled && support?.isSupported == true

            if (lastReportedAcceleration != acceleration) {
                logger.info(
                    "Browser GPU acceleration requested={}, enabled={}, beta={}",
                    enabled,
                    acceleration,
                    support?.isBeta ?: false
                )
                lastReportedAcceleration = acceleration
            }

            return acceleration
        }

        private fun currentFrameRateLimit(): Int {
            val frameRate = Minecraft.getInstance().options.framerateLimit().get()
            if (lastReportedFrameRateLimit != frameRate) {
                logger.info("Browser target frame rate follows Minecraft limit={}", frameRate)
                lastReportedFrameRateLimit = frameRate
            }
            return frameRate
        }

        fun prewarmBrowserIfNeeded() {
            if (prewarmAttempted) {
                return
            }

            prewarmAttempted = true
            try {
                obtainSharedBrowser()
                logger.info("Browser prewarm completed")
            } catch (exception: Exception) {
                prewarmAttempted = false
                logger.error("Failed to prewarm browser", exception)
            }
        }

        var INSTANCE: BasicBrowser? = null
        /**
         * 处理收到的GUI加载ACK包（静态方法，供处理器调用）
         */
        fun handleAck(ack: GuiLoadAckPacket) {
            INSTANCE?.handleGuiLoadAck(ack)
        }
    }

    enum class Mode(val id: String) {
        CLICKGUI("clickgui"),
        OOBE("oobe"),
        MAINMENU("mainmenu")
    }
}
