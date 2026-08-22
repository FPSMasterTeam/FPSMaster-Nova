package top.fpsmaster.web

import net.ccbluex.liquidbounce.mcef.MCEFAccelerationSupport
import net.minecraft.client.Minecraft
//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.client.gui.screens.Screen
//? if >=1.21.11 {
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
//?}
import net.minecraft.network.chat.Component
import top.fpsmaster.Client
import top.fpsmaster.logger
import top.fpsmaster.module.impl.render.ClickGUI
import top.fpsmaster.web.cef.ClientBrowser

/** A standalone WebView renderer. FPSMaster product UI does not use this screen. */
open class BasicBrowser(private val url: String) : Screen(Component.literal("WebView")) {
    private var browser: ClientBrowser? = null

    override fun init() {
        super.init()
        if (Client.isCefReady()) {
            browser = ClientBrowser(
                url,
                fps = Minecraft.getInstance().options.framerateLimit().get(),
                accelerate = shouldUseAcceleration()
            ).also { it.resize(width, height) }
        }
    }

    override fun removed() {
        browser?.close()
        browser = null
        super.removed()
    }

    //? if >=1.21.11 {
    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        browser?.resize(this.width, this.height)
    }
    //?} else {
    /*override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        browser?.resize(this.width, this.height)
    }
    *///?}

    //? if >=26 {
    /*override fun extractRenderState(g: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val guiGraphics = GuiGraphics(g)
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        renderBrowser(guiGraphics)
    }
    *///?}
    //? if >=1.20 && <26 {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderBrowser(guiGraphics)
    }
    //?}
    //? if <1.20 {
    /*override fun render(poseStack: com.mojang.blaze3d.vertex.PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(poseStack, mouseX, mouseY, partialTick)
        renderBrowser(GuiGraphics(poseStack))
    }
    *///?}

    private fun renderBrowser(guiGraphics: GuiGraphics) {
        val current = browser
        if (current != null) {
            current.render(guiGraphics, width, height)
            return
        }
        guiGraphics.fill(0, 0, width, height, 0xE8080810.toInt())
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Client.cefFailureMessage ?: "WebView 不可用",
            width / 2,
            height / 2,
            0xFFFF6B6B.toInt()
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
    }
    *///?}

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.sendMouseMove(mouseX, mouseY)
        super.mouseMoved(mouseX, mouseY)
    }

    //? if >=1.21.11 {
    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean =
        super.mouseDragged(event, mouseX, mouseY)
    //?} else {
    /*override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean =
        super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    *///?}

    //? if >=1.20.5 {
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        browser?.sendMouseWheel(mouseX, mouseY, scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
    //?} else {
    /*override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        browser?.sendMouseWheel(mouseX, mouseY, scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollY)
    }
    *///?}

    //? if >=1.21.11 {
    override fun keyPressed(event: KeyEvent): Boolean {
        browser?.sendKeyPress(event.key(), event.scancode().toLong(), event.modifiers())
        return super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        browser?.sendKeyRelease(event.key(), event.scancode().toLong(), event.modifiers())
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val text = event.codepointAsString()
        if (text.isEmpty()) return super.charTyped(event)
        if (text[0].code > 0x7e || text.length > 1) {
            browser?.insertText(text)
        } else {
            //? if >=26 {
            /*browser?.sendKeyTyped(text[0], 0)
            *///?} else {
            browser?.sendKeyTyped(text[0], event.modifiers())
            //?}
        }
        return super.charTyped(event)
    }
    //?} else {
    /*override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        browser?.sendKeyPress(keyCode, scanCode.toLong(), modifiers)
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        browser?.sendKeyRelease(keyCode, scanCode.toLong(), modifiers)
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (chr.code > 0x7e) browser?.insertText(chr.toString())
        else browser?.sendKeyTyped(chr, modifiers)
        return super.charTyped(chr, modifiers)
    }
    *///?}

    override fun isPauseScreen(): Boolean = false

    companion object {
        fun isAccelerationAvailable(): Boolean {
            return try {
                val nativeSupport = MCEFAccelerationSupport.getAccelerationSupport().isSupported
                //? if >=1.21.5 {
                val displaySupport = top.fpsmaster.web.cef.AcceleratedBrowserTexture.probe()
                //?} else {
                /*val displaySupport = true
                *///?}
                nativeSupport && displaySupport
            } catch (_: Throwable) {
                false
            }
        }

        private fun shouldUseAcceleration(): Boolean {
            val acceleration = ClickGUI.hardwareAcceleration.getValue() && isAccelerationAvailable()
            logger.info("WebView GPU acceleration enabled={}", acceleration)
            return acceleration
        }
    }
}
