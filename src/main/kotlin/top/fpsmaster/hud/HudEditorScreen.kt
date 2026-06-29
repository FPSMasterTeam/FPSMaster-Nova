package top.fpsmaster.hud

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
//? if >=1.21.5 {
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
//?}
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import top.fpsmaster.module.impl.render.HudEditor

class HudEditorScreen(
    private val owner: HudEditor
) : Screen(Component.literal("HUD Editor")) {
    private var activeComponent: HudComponent? = null
    private var resizing = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0x55000000)
        guiGraphics.drawString(font, "HUD Editor", 12, 12, 0xFFFFFFFF.toInt(), true)
        guiGraphics.drawString(font, "Left drag to move, drag the bottom-right handle to resize, ESC to close", 12, 26, 0xFFD0D0D0.toInt(), false)

        HudManager.components.values.forEach { component ->
            val selected = component === activeComponent
            val hovered = component.contains(mouseX.toFloat(), mouseY.toFloat(), preview = true)
            drawBounds(guiGraphics, component, selected || hovered)
            component.render(guiGraphics, preview = true)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    //? if >=1.21.5 {
    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, isDoubleClick)
        }

        val mouseX = event.x().toFloat()
        val mouseY = event.y().toFloat()
        val component = HudManager.components.values.toList()
            .asReversed()
            .firstOrNull {
                it.resizeHandleContains(mouseX, mouseY, preview = true) ||
                    it.contains(mouseX, mouseY, preview = true)
            } ?: return super.mouseClicked(event, isDoubleClick)

        activeComponent = component
        resizing = component.resizeHandleContains(mouseX, mouseY, preview = true)
        if (!resizing) {
            dragOffsetX = mouseX - component.x
            dragOffsetY = mouseY - component.y
        }
        return true
    }
    //?} else {
    /*override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button)
        }

        val mx = mouseX.toFloat()
        val my = mouseY.toFloat()
        val component = HudManager.components.values.toList()
            .asReversed()
            .firstOrNull {
                it.resizeHandleContains(mx, my, preview = true) ||
                    it.contains(mx, my, preview = true)
            } ?: return super.mouseClicked(mouseX, mouseY, button)

        activeComponent = component
        resizing = component.resizeHandleContains(mx, my, preview = true)
        if (!resizing) {
            dragOffsetX = mx - component.x
            dragOffsetY = my - component.y
        }
        return true
    }*/
    //?}

    //? if >=1.21.5 {
    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(event, mouseX, mouseY)
        }

        val component = activeComponent ?: return super.mouseDragged(event, mouseX, mouseY)
        val currentX = event.x().toFloat()
        val currentY = event.y().toFloat()

        if (resizing) {
            component.resizeTo(currentX, currentY, width.toFloat(), height.toFloat(), preview = true)
        } else {
            component.moveTo(currentX, currentY, dragOffsetX, dragOffsetY, width.toFloat(), height.toFloat(), preview = true)
        }
        return true
    }
    //?} else {
    /*override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        }

        val component = activeComponent ?: return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        val currentX = mouseX.toFloat()
        val currentY = mouseY.toFloat()

        if (resizing) {
            component.resizeTo(currentX, currentY, width.toFloat(), height.toFloat(), preview = true)
        } else {
            component.moveTo(currentX, currentY, dragOffsetX, dragOffsetY, width.toFloat(), height.toFloat(), preview = true)
        }
        return true
    }*/
    //?}

    //? if >=1.21.5 {
    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (activeComponent != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            activeComponent = null
            resizing = false
            HudConfigManager.save()
            return true
        }

        return super.mouseReleased(event)
    }
    //?} else {
    /*override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (activeComponent != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            activeComponent = null
            resizing = false
            HudConfigManager.save()
            return true
        }

        return super.mouseReleased(mouseX, mouseY, button)
    }*/
    //?}

    //? if >=1.21.5 {
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }

        return super.keyPressed(event)
    }
    //?} else {
    /*override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }*/
    //?}

    override fun removed() {
        HudConfigManager.save()
        owner.onEditorClosed()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    private fun drawBounds(guiGraphics: GuiGraphics, component: HudComponent, highlight: Boolean) {
        val x = component.x.toInt()
        val y = component.y.toInt()
        val right = (component.x + component.width(preview = true)).toInt()
        val bottom = (component.y + component.height(preview = true)).toInt()
        val outlineColor = if (highlight) 0xFF55C1FF.toInt() else 0xAAFFFFFF.toInt()
        val fillColor = if (highlight) 0x221EA7FD else 0x16000000

        guiGraphics.fill(x, y, right, bottom, fillColor)
        guiGraphics.fill(x, y, right, y + 1, outlineColor)
        guiGraphics.fill(x, bottom - 1, right, bottom, outlineColor)
        guiGraphics.fill(x, y, x + 1, bottom, outlineColor)
        guiGraphics.fill(right - 1, y, right, bottom, outlineColor)
        guiGraphics.fill(right - HudComponent.RESIZE_HANDLE_SIZE.toInt(), bottom - HudComponent.RESIZE_HANDLE_SIZE.toInt(), right, bottom, outlineColor)
        guiGraphics.drawString(font, component.id, x, y - 10, outlineColor, false)
    }
}
