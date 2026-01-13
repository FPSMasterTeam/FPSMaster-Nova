package top.fpsmaster.web

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowser
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowserSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.network.CefRequest
import top.fpsmaster.logger
import top.fpsmaster.web.network.NetworkManager
import top.fpsmaster.web.network.packets.GuiLoadAckPacket
import top.fpsmaster.web.network.packets.GuiLoadEventPacket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

var browser: MCEFBrowser? = null
var currentBrowser: BasicBrowser? = null

class LoadHandler: CefLoadHandler{
    override fun onLoadingStateChange(
        p0: CefBrowser?,
        p1: Boolean,
        p2: Boolean,
        p3: Boolean
    ) {
        logger.info("onLoadingStateChange")
    }

    override fun onLoadStart(
        p0: CefBrowser?,
        p1: CefFrame?,
        p2: CefRequest.TransitionType?
    ) {
        logger.info("onLoadStart")
    }

    override fun onLoadEnd(p0: CefBrowser?, p1: CefFrame?, p2: Int) {
        logger.info("onLoadEnd")
    }

    override fun onLoadError(
        p0: CefBrowser?,
        p1: CefFrame?,
        p2: CefLoadHandler.ErrorCode?,
        p3: String?,
        p4: String?
    ) {
        logger.info("onLoadError $p3 $p4")
    }

}

var bgra_shader: RenderPipeline? = null;
var texture_shader: RenderPipeline? = null;


class ShaderShaderSource: ShaderSource{
    val shaders = HashMap<Identifier, String>()

    init {
        loadShader(Identifier.fromNamespaceAndPath("fpsmaster", "shaders/bgra.frag"))
    }

    fun loadShader(identifier: Identifier){
        shaders[identifier] = Minecraft.getInstance().resourceManager.getResource(identifier).get().openAsReader().readLines().joinToString("\n")
    }

    override fun get(
        identifier: Identifier,
        shaderType: ShaderType
    ): String? {
        return shaders[identifier]
    }
}

class BasicBrowser : Screen(Component.literal("Browser")) {

    private val minecraft: Minecraft = Minecraft.getInstance()

    // 通过服务器广播与前端通信

    // ACK等待机制
    private var ackFuture: CompletableFuture<GuiLoadAckPacket>? = null
    private var waitingForAck = false
    private val ACK_TIMEOUT_MS = 5000L  // 5秒超时
    private var closingRequested = false
    private var closeAckReceived = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
//        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        
    }
    /**
     * 发送GUI加载事件并等待ACK
     * @return 是否成功收到ACK
     */
    private fun sendGuiLoadEvent(): Boolean {
        try {
            var waitMs = 0
            while (top.fpsmaster.web.network.NetworkManager.getConnectionCount() <= 0 && waitMs < 2000) {
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
            val transparent = true
            var newResourceManager = MCEF.INSTANCE.newResourceManager()
            if (newResourceManager.requiresDownload())
                newResourceManager.downloadJcef()
            MCEF.INSTANCE.initialize()
            val mcefBrowserSettings = MCEFBrowserSettings(120, true)
            MCEF.INSTANCE.client.addLoadHandler(LoadHandler())
            browser = MCEF.INSTANCE.createBrowser(url, transparent, mcefBrowserSettings)
            // load shaders

            val builder = RenderPipeline.Builder()
            bgra_shader = builder
                .withLocation(Identifier.fromNamespaceAndPath("fpsmaster", "pipeline/jcef/bgra_blurred_texture"))
                .apply {
                    withVertexShader("core/position_tex_color")
                    val identifier = Identifier.fromNamespaceAndPath("fpsmaster", "shaders/bgra.frag")
                    withFragmentShader(identifier)
                    withSampler("Sampler0")
                    withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    withSnippet(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                    withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                }.build();

            texture_shader = builder
                .withLocation(Identifier.fromNamespaceAndPath("fpsmaster", "browser"))
                .apply {
                    withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                    withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                }.build()


            val source = ShaderShaderSource()
            RenderSystem.getDevice().precompilePipeline(bgra_shader!!, source)
            RenderSystem.getDevice().precompilePipeline(texture_shader!!, source)
        }
        resizeBrowser()
    }



    var loaded = false
    var ackReceived = false

    override fun init() {
        super.init()
        initBrowser()

        // 设置当前浏览器实例
        currentBrowser = this

        // 发送GUI加载事件并等待ACK
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

    private fun mouseX(x: Double): Int {
        return ((x - BROWSER_DRAW_OFFSET) * minecraft.window.guiScale).toInt()
    }

    private fun mouseY(y: Double): Int {
        return ((y - BROWSER_DRAW_OFFSET) * minecraft.window.guiScale).toInt()
    }

    private fun scaleX(x: Double): Int {
        return ((x - BROWSER_DRAW_OFFSET * 2) * minecraft.window.guiScale).toInt()
    }

    private fun scaleY(y: Double): Int {
        return ((y - BROWSER_DRAW_OFFSET * 2) * minecraft.window.guiScale).toInt()
    }

    private fun resizeBrowser() {
        if (width > 100 && height > 100) {
            browser!!.clear()
            browser!!.resize(scaleX(width.toDouble()), scaleY(height.toDouble()))
        }
    }

    override fun resize(i: Int, j: Int) {
        super.resize(i, j)
        resizeBrowser()
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

        // 检查是否收到ACK，未收到ACK则不绘制UI
        if (!ackReceived) {
            // 可选：显示加载动画或等待提示
            return
        }

        if (!loaded) {
            return
        }
        if (!browser!!.renderer.isTextureReady || browser!!.renderer.isUnpainted) {
            return
        }

        val textureSetup = browser!!.renderer.textureSetup
        val bgra = browser!!.renderer.isBGRA
        var pipeline: RenderPipeline? = null
        if (bgra) {
            pipeline = bgra_shader;
        } else {
            pipeline = texture_shader;
        }
        guiGraphics.guiRenderState.submitGuiElement(
            TexQuadGuiElementRenderState(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                0f,
                0f,
                1f,
                1f,
                -1,
                pipeline,
                textureSetup,
                guiGraphics.pose(),
                guiGraphics.scissorStack.peek(),
                createBounds(0, 0, width, height)
            )
        )
    }

    fun createBounds(x: Int, y: Int, w: Int, h: Int): ScreenRectangle {
        return ScreenRectangle(x, y, w, h)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        browser!!.sendMousePress(mouseX(event.x()), mouseY(event.y()), event.button())
        browser!!.setFocus(true)
        return super.mouseClicked(event, isDoubleClick)
    }


    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        browser!!.sendMouseRelease(mouseX(event.x()), mouseY(event.y()), event.button())
        browser!!.setFocus(true)
        return super.mouseReleased(event)
    }


    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser!!.sendMouseMove(mouseX(mouseX), mouseY(mouseY))
        super.mouseMoved(mouseX, mouseY)
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        return super.mouseDragged(event, mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        browser!!.sendMouseWheel(mouseX(mouseX), mouseY(mouseY), scrollY)
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
        browser!!.setFocus(true)
        return super.keyPressed(event)
    }


    override fun keyReleased(event: KeyEvent): Boolean {
        browser!!.sendKeyRelease(event.key(), event.scancode().toLong(), event.modifiers())
        browser!!.setFocus(true)
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (event.codepoint() == 0.toChar().code) return false
        browser!!.sendKeyTyped(event.codepointAsString()[0], event.modifiers())
        browser!!.setFocus(true)
        return super.charTyped(event)
    }

    companion object {
        private const val BROWSER_DRAW_OFFSET = 0

        /**
         * 处理收到的GUI加载ACK包（静态方法，供处理器调用）
         */
        fun handleAck(ack: GuiLoadAckPacket) {
            currentBrowser?.handleGuiLoadAck(ack)
        }
    }
}
