package top.fpsmaster.hud.impl

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.ReachDisplay

class ReachTextHudComponent : HudComponent(
    id = "reach_text",
    x = 10f,
    y = 336f
) {
    override fun shouldRender(): Boolean = visible && ReachDisplay.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val text = if (preview) "3.12 b" else ReachDisplay.text()
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        ReachDisplay.style.drawText(guiGraphics, if (preview) "3.12 b" else ReachDisplay.text(), 0, 0, ReachDisplay.textColorValue())
    }
}
