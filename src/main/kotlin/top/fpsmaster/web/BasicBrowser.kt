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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BasicBrowser : Screen(Component.literal("Browser")) {
    private var ackFuture: CompletableFuture<GuiLoadAckPacket>? = null
    private var waitingForAck = false
    private val ACK_TIMEOUT_MS = 5000L  // 5秒超时
    private var closingRequested = false
    private var closeAckReceived = false
    private var browser: ClientBrowser? = null;

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick)
    }
    /**
     * 发送GUI加载事件并等待ACK
     * @return 是否成功收到ACK
     */
    private fun sendGuiLoadEvent(): Boolean {
        try {
            var waitMs = 0
            while (NetworkManager.getConnectionCount() <= 0 && waitMs < 2000) {
                Thread.sleep(100)
                waitMs += 100
            }
            // 创建并发送GUI加载事件
            val eventPacket = GuiLoadEventPacket().apply {
                eventType = "open"
                timestamp = System.currentTimeMillis()
                extraData = "browser"
            }

            logger.info("Sending GUI load event: $eventPacket")
            NetworkManager.broadcastPacket(eventPacket)

            // 等待ACK
            waitingForAck = true
            ackFuture = CompletableFuture()

            try {
                val ack = ackFuture?.get(ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                waitingForAck = false

                if (ack != null && ack.success) {
                    logger.info("Received GUI load ACK: ${ack.message}")
                    return true
                } else {
                    logger.warn("GUI load ACK failed or timeout")
                    return false
                }
            } catch (e: TimeoutException) {
                logger.error("GUI load ACK timeout after ${ACK_TIMEOUT_MS}ms")
                waitingForAck = false
                return false
            }

        } catch (e: Exception) {
            logger.error("Failed to send GUI load event", e)
            waitingForAck = false
            return false
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
        if (waitingForAck && ackFuture != null) {
            ackFuture?.complete(ack)
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
            val url = "http://localhost:3000/"
            browser = ClientBrowser(url)
        }
        browser!!.resize(width, height)
        INSTANCE = this
    }



    var loaded = false
    var ackReceived = false

    override fun init() {
        super.init()
        initBrowser()
        val ackSuccess = sendGuiLoadEvent()
        if (ackSuccess) {
            logger.info("GUI load ACK received, browser will render")
            ackReceived = true
        } else {
            logger.warn("GUI load ACK not received, browser may not display correctly")
            // 即使没有收到ACK，也继续加载浏览器（可选）
            ackReceived = true
        }

        loaded = true
    }




    override fun resize(i: Int, j: Int) {
        super.resize(i, j)
        browser!!.resize(width, height)
    }


    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
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
        var INSTANCE: BasicBrowser? = null
        /**
         * 处理收到的GUI加载ACK包（静态方法，供处理器调用）
         */
        fun handleAck(ack: GuiLoadAckPacket) {
            INSTANCE?.handleGuiLoadAck(ack)
        }
    }
}
