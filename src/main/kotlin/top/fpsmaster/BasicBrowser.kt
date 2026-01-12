package top.fpsmaster

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
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

var browser: MCEFBrowser? = null

class BasicBrowser : Screen(Component.literal("Browser")) {

    private val minecraft: Minecraft = Minecraft.getInstance()

    fun initBrowser() {
        if (browser == null) {
            val url = "http://localhost:3000/"
            val transparent = true
            var newResourceManager = MCEF.INSTANCE.newResourceManager()
            if (newResourceManager.requiresDownload())
                newResourceManager.downloadJcef()
            MCEF.INSTANCE.initialize()
            val mcefBrowserSettings = MCEFBrowserSettings(60, false)
            browser = MCEF.INSTANCE.createBrowser(url, transparent, mcefBrowserSettings)
            resizeBrowser()
        }
    }

    var loaded = false;

    override fun init() {
        super.init()
        initBrowser()
        browser!!.clear()
//        browser!!.reload()
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
            browser!!.resize(scaleX(width.toDouble()), scaleY(height.toDouble()))
        }
    }

    override fun resize(i: Int, j: Int) {
        super.resize(i, j)
        resizeBrowser()
    }


    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        if (!loaded) {
            return
        }
        val renderer = browser!!.renderer
        MCEF.INSTANCE.app.handle.N_DoMessageLoopWork()

        if (!renderer.isTextureReady || renderer.isUnpainted) {
            return
        }
        val textureSetup = renderer.textureSetup
        val bgra = renderer.isBGRA
        var pipeline: RenderPipeline? = null
        val builder = RenderPipeline.Builder()
        if (bgra) {
            pipeline = builder
                .withLocation(Identifier.fromNamespaceAndPath("fpsmaster", "jcef/bgra_blurred_texture"))
                .apply {
                    withVertexShader("core/position_tex_color")
                    withFragmentShader(Identifier.fromNamespaceAndPath("fpsmaster","shader/bgra_position_tex_color.frag"))
                    withSampler("Sampler0")
                    withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    withSnippet(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                    withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            }.build()
        } else {
            pipeline = builder
                .withLocation(Identifier.fromNamespaceAndPath("fpsmaster", "browser"))
                .apply {
                    withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                    withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            }.build()
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

    var devtools = false;

    override fun keyPressed(event: KeyEvent): Boolean {
        browser!!.sendKeyPress(event.key(), event.scancode().toLong(), event.modifiers())
        browser!!.setFocus(true)
        if (event.key() == GLFW.GLFW_KEY_P){
            if (!devtools){
                browser!!.openDevTools()
            } else {
                browser!!.closeDevTools()
            }
        }
        return super.keyPressed(event)
    }


    override fun keyReleased(event: KeyEvent): Boolean {
        browser!!.sendKeyRelease(event.key(), event.scancode().toLong(), event.modifiers())
        browser!!.setFocus(true)
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (event.codepoint() == 0.toChar().code) return false
        browser!!.sendKeyTyped(event.codepointAsString().get(0), event.modifiers())
        browser!!.setFocus(true)
        return super.charTyped(event)
    }

    companion object {
        private const val BROWSER_DRAW_OFFSET = 0
    }
}