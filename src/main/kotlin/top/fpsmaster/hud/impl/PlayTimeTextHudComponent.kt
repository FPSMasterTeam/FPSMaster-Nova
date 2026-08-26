package top.fpsmaster.hud.impl

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.PlayTime

class PlayTimeTextHudComponent : HudComponent(
    id = "play_time",
    x = 10f,
    y = 206f
) {
    override fun shouldRender(): Boolean = visible && PlayTime.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        PlayTime.style.drawText(guiGraphics, resolveText(preview), 0, 0, PlayTime.textColorValue())
    }

    private fun resolveText(preview: Boolean): String {
        val time = if (preview) "01:23:45" else PlayTime.currentText()
        val label = PlayTime.label.getValue().trim()
        return if (label.isEmpty()) time else "$label $time"
    }
}
