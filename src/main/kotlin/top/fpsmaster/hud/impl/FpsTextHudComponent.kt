package top.fpsmaster.hud.impl

import net.minecraft.client.gui.GuiGraphics
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc

class FpsTextHudComponent : HudComponent(
    id = "fps_text",
    x = 10f,
    y = 46f
) {
    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        guiGraphics.drawString(mc.font, resolveText(preview), 0, 0, 0xFFFFFFFF.toInt(), true)
    }

    private fun resolveText(preview: Boolean): String {
        return if (preview) {
            "FPS 240"
        } else {
            "FPS ${mc.fps}"
        }
    }
}
