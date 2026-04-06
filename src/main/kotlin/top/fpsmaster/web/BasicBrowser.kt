package top.fpsmaster.web

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import top.fpsmaster.logger
import top.fpsmaster.web.cef.ClientBrowser
import top.fpsmaster.web.network.NetworkManager
import top.fpsmaster.web.network.packets.GuiLoadAckPacket
import top.fpsmaster.web.network.packets.GuiLoadEventPacket

class BasicBrowser : Screen(Component.literal("Browser")) {
    private val ACK_TIMEOUT_MS = 5000L  // 5秒超时
    private var closingRequested = false
    private var closeAckReceived = false
    private var browser: ClientBrowser? = null
    private var openEventSent = false
    private var waitingForOpenAck = false
    private var openEventSentAt = 0L
    private var openAckTimedOut = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
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
                extraData = "browser"
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
                extraData = "browser"
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
            } else {
                logger.warn("Received unexpected GUI load ACK: $ack")
            }
        }
    }

    fun initBrowser() {
        if (browser == null) {
            browser = obtainSharedBrowser()
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
        browser!!.resize(width, height)
    }


    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        trySendGuiLoadEvent()
        updateOpenAckState()

        // 在渲染线程处理关闭逻辑，避免跨线程调用setScreen
        if (closingRequested && closeAckReceived) {
            closingRequested = false
            closeAckReceived = false
            Minecraft.getInstance().setScreen(null)
            return
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        if (!ackReceived) {
            return
        }

        browser!!.render(guiGraphics, width, height)
    }


    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        browser!!.mouseClicked(event.x(), event.y(), event.button())
        return super.mouseClicked(event, isDoubleClick)
    }


    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        browser!!.mouseReleased(event.x(), event.y(), event.button())
        return super.mouseReleased(event)
    }


    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser!!.sendMouseMove(mouseX, mouseY)
        super.mouseMoved(mouseX, mouseY)
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        return super.mouseDragged(event, mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        browser!!.sendMouseWheel(mouseX, mouseY, scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && !closingRequested) {
            closingRequested = true
            val ok = sendGuiCloseEvent()
            if (!ok) {
                closingRequested = false
            }
            return false
        }
        browser!!.sendKeyPress(event.key(), event.scancode().toLong(), event.modifiers())
        return super.keyPressed(event)
    }


    override fun keyReleased(event: KeyEvent): Boolean {
        browser!!.sendKeyRelease(event.key(), event.scancode().toLong(), event.modifiers())
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (event.codepoint() == 0.toChar().code) return false
        browser!!.sendKeyTyped(event.codepointAsString()[0], event.modifiers())
        return super.charTyped(event)
    }

    companion object {
        private const val BROWSER_URL = "http://localhost:3000/"
        private var sharedBrowser: ClientBrowser? = null
        private var prewarmAttempted = false

        private fun currentGuiWidth(): Int {
            val window = Minecraft.getInstance().window
            return (window.width / window.guiScale).toInt()
        }

        private fun currentGuiHeight(): Int {
            val window = Minecraft.getInstance().window
            return (window.height / window.guiScale).toInt()
        }

        private fun obtainSharedBrowser(): ClientBrowser {
            if (sharedBrowser == null) {
                logger.info("Creating shared browser instance")
                sharedBrowser = ClientBrowser(BROWSER_URL)
            }

            val browser = sharedBrowser!!
            val guiWidth = currentGuiWidth()
            val guiHeight = currentGuiHeight()
            if (guiWidth > 0 && guiHeight > 0) {
                browser.resize(guiWidth, guiHeight)
            }
            return browser
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
}
