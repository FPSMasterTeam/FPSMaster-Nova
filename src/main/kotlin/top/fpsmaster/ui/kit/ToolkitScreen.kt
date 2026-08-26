package top.fpsmaster.ui.kit

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
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
import org.lwjgl.glfw.GLFW
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.prism.input.FrameInput
import top.fpsmaster.prism.input.Keys
import top.fpsmaster.prism.theme.Theme
import top.fpsmaster.prism.widget.UiFrame

abstract class ToolkitScreen(title: Component) : Screen(title) {
    protected val frameInput = FrameInput()

    protected abstract fun renderUi(ui: UiFrame)

    protected fun theme(): Theme {
        val light = ClientSettings.theme.getValue().toInt() == 1
        return Theme.of(light, NovaBlur.enabled())
    }

    //? if >=26 {
    /*override fun extractRenderState(g: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val guiGraphics = GuiGraphics(g)
        renderToolkitBackground(guiGraphics, partialTick)
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        paint(guiGraphics, mouseX, mouseY)
    }*/
    //?}
    //? if >=1.20 && <26 {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderToolkitBackground(guiGraphics, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        paint(guiGraphics, mouseX, mouseY)
    }
    //?}
    //? if <1.20 {
    /*override fun render(poseStack: com.mojang.blaze3d.vertex.PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val guiGraphics = GuiGraphics(poseStack)
        renderToolkitBackground(guiGraphics, partialTick)
        super.render(poseStack, mouseX, mouseY, partialTick)
        paint(guiGraphics, mouseX, mouseY)
    }*/
    //?}

    protected open fun renderToolkitBackground(guiGraphics: GuiGraphics, partialTick: Float) {}

    private fun paint(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        //? if <1.21.5 {
        /*com.mojang.blaze3d.systems.RenderSystem.enableBlend()
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc()
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f)*/
        //?}
        try {
            frameInput.setMouse(mouseX, mouseY)
            val canvas = NovaCanvas(guiGraphics, font)
            val host = NovaHost(canvas, frameInput, font, width.toFloat(), height.toFloat())
            renderUi(UiFrame(host, theme()))
            frameInput.endFrame()
        } finally {
            //? if <1.21.5 {
            /*com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            com.mojang.blaze3d.systems.RenderSystem.disableBlend()*/
            //?}
        }
    }

    //? if >=1.21.11 {
    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        frameInput.press(event.button(), event.x().toInt(), event.y().toInt())
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        frameInput.release(event.button())
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        mapKey(event.key()).let { frameInput.pressKey(it) }
        frameInput.pressRawKey(event.key())
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            return handleEscape()
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val s = event.codepointAsString()
        if (s.isNotEmpty() && !s[0].isISOControl()) {
            frameInput.type(s)
        }
        return true
    }
    //?} else {
    /*override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        frameInput.press(button, mouseX.toInt(), mouseY.toInt())
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        frameInput.release(button)
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        mapKey(keyCode).let { frameInput.pressKey(it) }
        frameInput.pressRawKey(keyCode)
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            return handleEscape()
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (!codePoint.isISOControl()) {
            frameInput.type(codePoint.toString())
        }
        return true
    }*/
    //?}

    //? if >=1.20.5 {
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        frameInput.addWheel(if (scrollY > 0) 1 else -1)
        return true
    }
    //?} else {
    /*override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        frameInput.addWheel(if (scrollY > 0) 1 else -1)
        return true
    }*/
    //?}

    override fun isPauseScreen(): Boolean = false

    /** ESC handler. Override to run a close animation instead of dismissing immediately. */
    protected open fun handleEscape(): Boolean {
        onClose()
        return true
    }

    private fun mapKey(glfw: Int): Int = when (glfw) {
        GLFW.GLFW_KEY_BACKSPACE -> Keys.BACKSPACE
        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> Keys.ENTER
        GLFW.GLFW_KEY_ESCAPE -> Keys.ESCAPE
        GLFW.GLFW_KEY_LEFT -> Keys.LEFT
        GLFW.GLFW_KEY_RIGHT -> Keys.RIGHT
        GLFW.GLFW_KEY_DELETE -> Keys.DELETE
        else -> glfw
    }
}
