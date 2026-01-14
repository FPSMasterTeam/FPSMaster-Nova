package top.fpsmaster.web.cef

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowser
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowserSettings
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import top.fpsmaster.mc
import top.fpsmaster.render.shaders.getShader
import top.fpsmaster.render.shaders.init
import top.fpsmaster.render.shaders.shaders
import top.fpsmaster.web.TexQuadGuiElementRenderState

class ClientBrowser(url: String, transparent: Boolean = true, var fps: Int = 60, accelerate: Boolean = true) {
    var url: String = url
        set(value) {
            field = value
            browser.loadURL(value)
        }
        get() = browser.url
    var browser: MCEFBrowser

    init {
        val mcefBrowserSettings = MCEFBrowserSettings(fps, accelerate)
        browser = MCEF.INSTANCE.createBrowser(url, transparent, mcefBrowserSettings)
        if (shaders.isEmpty()){
            init()
        }
    }



    fun render(guiGraphics: GuiGraphics, width: Int, height: Int) {

        if (!browser.renderer.isTextureReady || browser.renderer.isUnpainted) {
            return
        }

        val textureSetup = browser.renderer.textureSetup
        val bgra = browser.renderer.isBGRA
        var pipeline: RenderPipeline? = null
        pipeline = if (bgra) {
            getShader("pipeline/jcef/bgra_blurred_texture");
        } else {
            getShader("pipeline/jcef/texture");
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

     fun resize(width: Int, height: Int) {
        browser.clear()
        browser.resize(scaleX(width.toDouble()), scaleY(height.toDouble()))
    }

    private fun mouseX(x: Double): Int {
        return (x * mc.window.guiScale).toInt()
    }

    private fun mouseY(y: Double): Int {
        return (y * mc.window.guiScale).toInt()
    }

    private fun scaleX(x: Double): Int {
        return (x * mc.window.guiScale).toInt()
    }

    private fun scaleY(y: Double): Int {
        return (y * mc.window.guiScale).toInt()
    }

    fun mouseClicked(x: Double, y: Double, button: Int) {
        browser.sendMousePress(mouseX(x), mouseY(y), button)
        browser.setFocus(true)
    }

    fun mouseReleased(x: Double, y: Double, button: Int) {
        browser.sendMouseRelease(mouseX(x), mouseY(y), button)
        browser.setFocus(true)
    }

    fun sendMouseMove(mouseX: Double, mouseY: Double) {
        browser.sendMouseMove(mouseX(mouseX), mouseY(mouseY))
        browser.setFocus(true)
    }

    fun sendMouseWheel(mouseX: Double, mouseY: Double, scrollY: Double) {
        browser.sendMouseWheel(mouseX(mouseX), mouseY(mouseY), scrollY)
        browser.setFocus(true)
    }

    fun sendKeyPress(key: Int, toLong: Long, modifiers: Int) {
        browser.sendKeyPress(key, toLong, modifiers)
        browser.setFocus(true)
    }

    fun sendKeyRelease(key: Int, toLong: Long, modifiers: Int) {
        browser.sendKeyRelease(key, toLong, modifiers)
        browser.setFocus(true)
    }

    fun sendKeyTyped(ch: Char, modifiers: Int) {
        browser.sendKeyTyped(ch, modifiers)
        browser.setFocus(true)
    }
}