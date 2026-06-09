package top.fpsmaster.web

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
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
    private var browser: ClientBrowser? = null
    private var openEventSent = false
    private var waitingForOpenAck = false
    private var openEventSentAt = 0L
    private var openAckTimedOut = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (BetterScreen.isActive()) {
            if (!BetterScreen.background.getValue()) {
                return
            }
            if (!BetterScreen.blur.getValue()) {
                renderTransparentBackground(guiGraphics)
                return
            }
        }
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick)
    }

    /**
     * 非阻塞发送GUI加载事件
     */
    private fun trySendGuiLoadEvent() {
        if (openEventSent || NetworkManager.getConnectionCount() <= 0) {
            return
        }

        try {
            val eventPacket = GuiLoadEventPacket().apply {
                eventType = "open"
                timestamp = System.currentTimeMillis()
                extraData = mode.id
            }

            logger.info("Sending GUI load event: $eventPacket")
            NetworkManager.broadcastPacket(eventPacket)
            openEventSent = true
            waitingForOpenAck = true
            openEventSentAt = System.currentTimeMillis()
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
        if (waitingForOpenAck) {
            waitingForOpenAck = false
            openAckTimedOut = false
            logger.info("GUI load ACK completed: $ack")
        } else {
            // 关闭ACK由前端在关闭动画完成后发送
            if (closingRequested) {
                closeAckReceived = true
                logger.info("GUI close ACK flagged for render thread: $ack")
            } else if (mode == Mode.OOBE && ack.message.startsWith("oobe:")) {
                finishOobe(ack.message.removePrefix("oobe:") == "settings")
            } else {
                logger.warn("Received unexpected GUI load ACK: $ack")
            }
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




    override fun resize(i: Int, j: Int) {
        super.resize(i, j)
        browser?.resize(width, height)
    }


    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        trySendGuiLoadEvent()
        updateOpenAckState()

        // 在渲染线程处理关闭逻辑，避免跨线程调用setScreen
        if (closingRequested && closeAckReceived) {
            closingRequested = false
            closeAckReceived = false
            Minecraft.getInstance().setScreen(if (mode == Mode.OOBE) TitleScreen() else null)
            return
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick)

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

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        browser?.mouseClicked(event.x(), event.y(), event.button())
        return super.mouseClicked(event, isDoubleClick)
    }


    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        browser?.mouseReleased(event.x(), event.y(), event.button())
        return super.mouseReleased(event)
    }


    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.sendMouseMove(mouseX, mouseY)
        super.mouseMoved(mouseX, mouseY)
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        return super.mouseDragged(event, mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        browser?.sendMouseWheel(mouseX, mouseY, scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (mode == Mode.OOBE && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return false
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && !closingRequested) {
            closingRequested = true
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
        browser?.sendKeyTyped(event.codepointAsString()[0], event.modifiers())
        return super.charTyped(event)
    }

    override fun shouldCloseOnEsc(): Boolean = mode != Mode.OOBE

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
            return "${resolveBrowserBaseUrl()}?view=${mode.id}"
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
            if (browser.url != targetUrl) {
                logger.info("Reloading shared browser to $targetUrl")
                browser.url = targetUrl
            }
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
        OOBE("oobe")
    }
}
