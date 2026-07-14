package top.fpsmaster.hud.impl

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.auxiliary.Sprint

class SprintTextHudComponent : HudComponent(
    id = "sprint_text",
    x = 10f,
    y = 10f
) {
    override fun shouldRender(): Boolean {
        return visible && ModuleManager.modules["sprint"]?.enabled == true && resolveText(preview = false).isNotBlank()
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
            ModuleManager.modules["sprint"]?.enabled == true && Sprint.isToggled() -> "[Sprinting (Toggled)]"
            player.isSprinting -> "[Sprinting (Vanilla)]"
            else -> ""
        }
    }
}
