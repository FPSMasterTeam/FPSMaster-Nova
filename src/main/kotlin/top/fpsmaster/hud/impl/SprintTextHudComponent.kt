package top.fpsmaster.hud.impl

import net.minecraft.client.gui.GuiGraphics
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.ModuleManager

class SprintTextHudComponent : HudComponent(
    id = "sprint_text",
    x = 10f,
    y = 10f
) {
    override fun shouldRender(): Boolean {
        return visible && resolveText(preview = false).isNotBlank()
    }

    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val text = resolveText(preview)
        if (text.isBlank()) {
            return
        }

        guiGraphics.drawString(mc.font, text, 0, 0, 0xFFFFFFFF.toInt(), true)
    }

    private fun resolveText(preview: Boolean): String {
        if (preview) {
            return "[Sprinting (Toggled)]"
        }

        val player = mc.player ?: return ""
        return when {
            player.abilities.flying -> "[Flying]"
            player.isSprinting && ModuleManager.modules["sprint"]?.enabled == true -> "[Sprinting (Toggled)]"
            player.isSprinting -> "[Sprinting]"
            else -> ""
        }
    }
}
