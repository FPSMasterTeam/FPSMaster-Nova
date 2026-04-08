package top.fpsmaster.hud.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc

class KeystrokesHudComponent : HudComponent(
    id = "keystrokes",
    x = 10f,
    y = 68f
) {
    override fun measure(preview: Boolean): HudSize {
        return HudSize(
            width = KEY_SIZE * 3 + KEY_GAP * 2,
            height = KEY_SIZE * 3 + KEY_GAP * 2
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        drawKey(guiGraphics, KEY_SIZE + KEY_GAP, 0f, KEY_SIZE, KEY_SIZE, "W", preview || mc.options.keyUp.isDown)
        drawKey(guiGraphics, 0f, KEY_SIZE + KEY_GAP, KEY_SIZE, KEY_SIZE, "A", preview || mc.options.keyLeft.isDown)
        drawKey(guiGraphics, KEY_SIZE + KEY_GAP, KEY_SIZE + KEY_GAP, KEY_SIZE, KEY_SIZE, "S", preview || mc.options.keyDown.isDown)
        drawKey(guiGraphics, (KEY_SIZE + KEY_GAP) * 2f, KEY_SIZE + KEY_GAP, KEY_SIZE, KEY_SIZE, "D", preview || mc.options.keyRight.isDown)
        drawKey(guiGraphics, 0f, (KEY_SIZE + KEY_GAP) * 2f, KEY_SIZE * 1.5f + KEY_GAP / 2f, KEY_SIZE, "LMB", preview || mc.options.keyAttack.isDown)
        drawKey(guiGraphics, KEY_SIZE * 1.5f + KEY_GAP * 1.5f, (KEY_SIZE + KEY_GAP) * 2f, KEY_SIZE * 1.5f + KEY_GAP / 2f, KEY_SIZE, "RMB", preview || mc.options.keyUse.isDown)
    }

    private fun drawKey(guiGraphics: GuiGraphics, x: Float, y: Float, width: Float, height: Float, text: String, pressed: Boolean) {
        val left = x.toInt()
        val top = y.toInt()
        val right = (x + width).toInt()
        val bottom = (y + height).toInt()
        val background = if (pressed) 0xCC4ADE80.toInt() else 0x88202020.toInt()
        val border = if (pressed) 0xFFF1FFF8.toInt() else 0xAAFFFFFF.toInt()

        guiGraphics.fill(left, top, right, bottom, background)
        guiGraphics.fill(left, top, right, top + 1, border)
        guiGraphics.fill(left, bottom - 1, right, bottom, border)
        guiGraphics.fill(left, top, left + 1, bottom, border)
        guiGraphics.fill(right - 1, top, right, bottom, border)

        val label = Component.literal(text)
        val textWidth = mc.font.width(label)
        val textX = left + ((width.toInt() - textWidth) / 2)
        val textY = top + ((height.toInt() - mc.font.lineHeight) / 2)
        guiGraphics.drawString(mc.font, label, textX, textY, 0xFFFFFFFF.toInt(), false)
    }

    companion object {
        private const val KEY_SIZE = 18f
        private const val KEY_GAP = 3f
    }
}
