package top.fpsmaster.hud

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
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
//?}
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

private const val SNAP_THRESHOLD = 6f

class HudEditorScreen : Screen(Component.literal("HUD Editor")) {
    private var activeComponent: HudComponent? = null
    private var resizing = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    // Alignment guide lines (screen coords) shown while a component snaps during a drag.
    private var guideX: Int? = null
    private var guideY: Int? = null

    // 26.2 deferred-render: render → extractRenderState(GuiGraphicsExtractor). Wrap into the shim so the
    // body (fill/drawString + component.render) is unchanged.
    //? if >=26 {
    /*override fun extractRenderState(g: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val guiGraphics = GuiGraphics(g)*/
    //?}
    //? if >=1.20 && <26 {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    //?}
    //? if <1.20 {
    /*override fun render(poseStack: com.mojang.blaze3d.vertex.PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val guiGraphics = GuiGraphics(poseStack)*/
    //?}
        guiGraphics.fill(0, 0, width, height, 0x55000000)
        guiGraphics.drawString(font, "HUD Editor", 12, 12, 0xFFFFFFFF.toInt(), true)
        guiGraphics.drawString(font, "Left drag to move, drag the bottom-right handle to resize, ESC to close", 12, 26, 0xFFD0D0D0.toInt(), false)

        HudManager.components.values.forEach { component ->
            val selected = component === activeComponent
            val hovered = component.contains(mouseX.toFloat(), mouseY.toFloat(), preview = true)
            drawBounds(guiGraphics, component, selected || hovered)
            component.render(guiGraphics, preview = true)
        }

        // Alignment guides for the current snap.
        guideX?.let { gx -> guiGraphics.fill(gx, 0, gx + 1, height, 0xFF55C1FF.toInt()) }
        guideY?.let { gy -> guiGraphics.fill(0, gy, width, gy + 1, 0xFF55C1FF.toInt()) }

        //? if >=26 {
        /*super.extractRenderState(g, mouseX, mouseY, partialTick)*/
        //?}
        //? if >=1.20 && <26 {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        //?}
        //? if <1.20 {
        /*super.render(poseStack, mouseX, mouseY, partialTick)*/
        //?}
    }

    //? if >=1.21.11 {
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

    //? if >=1.21.11 {
    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(event, mouseX, mouseY)
        }

        val component = activeComponent ?: return super.mouseDragged(event, mouseX, mouseY)
        val currentX = event.x().toFloat()
        val currentY = event.y().toFloat()

        if (resizing) {
            guideX = null
            guideY = null
            component.resizeTo(currentX, currentY, width.toFloat(), height.toFloat(), preview = true)
        } else {
            component.moveTo(currentX, currentY, dragOffsetX, dragOffsetY, width.toFloat(), height.toFloat(), preview = true)
            applySnap(component, width.toFloat(), height.toFloat())
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
            guideX = null
            guideY = null
            component.resizeTo(currentX, currentY, width.toFloat(), height.toFloat(), preview = true)
        } else {
            component.moveTo(currentX, currentY, dragOffsetX, dragOffsetY, width.toFloat(), height.toFloat(), preview = true)
            applySnap(component, width.toFloat(), height.toFloat())
        }
        return true
    }*/
    //?}

    //? if >=1.21.11 {
    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (activeComponent != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            activeComponent = null
            resizing = false
            guideX = null
            guideY = null
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
            guideX = null
            guideY = null
            HudConfigManager.save()
            return true
        }

        return super.mouseReleased(mouseX, mouseY, button)
    }*/
    //?}

    //? if >=1.21.11 {
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
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    /**
     * Snap the dragged component's left/center/right edges to the screen edges/center and to any other
     * component's edges/center when within [SNAP_THRESHOLD] px, recording the matched line so the editor
     * can draw an alignment guide. Called after a plain move (never while resizing).
     */
    private fun applySnap(c: HudComponent, maxW: Float, maxH: Float) {
        val w = c.width(preview = true)
        val h = c.height(preview = true)
        val vLines = mutableListOf(0f, maxW / 2f, maxW)
        val hLines = mutableListOf(0f, maxH / 2f, maxH)
        for (o in HudManager.components.values) {
            if (o === c) continue
            val ow = o.width(preview = true)
            val oh = o.height(preview = true)
            vLines.add(o.x); vLines.add(o.x + ow / 2f); vLines.add(o.x + ow)
            hLines.add(o.y); hLines.add(o.y + oh / 2f); hLines.add(o.y + oh)
        }

        guideX = null
        guideY = null

        // xPoints/yPoints captured against the pre-snap position so all three edges test consistently.
        var bestX = SNAP_THRESHOLD
        for ((offset, point) in listOf(0f to c.x, w / 2f to c.x + w / 2f, w to c.x + w)) {
            for (line in vLines) {
                val d = kotlin.math.abs(point - line)
                if (d <= bestX) {
                    bestX = d
                    c.x = (line - offset).coerceIn(0f, (maxW - w).coerceAtLeast(0f))
                    guideX = line.toInt()
                }
            }
        }
        var bestY = SNAP_THRESHOLD
        for ((offset, point) in listOf(0f to c.y, h / 2f to c.y + h / 2f, h to c.y + h)) {
            for (line in hLines) {
                val d = kotlin.math.abs(point - line)
                if (d <= bestY) {
                    bestY = d
                    c.y = (line - offset).coerceIn(0f, (maxH - h).coerceAtLeast(0f))
                    guideY = line.toInt()
                }
            }
        }
    }

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
