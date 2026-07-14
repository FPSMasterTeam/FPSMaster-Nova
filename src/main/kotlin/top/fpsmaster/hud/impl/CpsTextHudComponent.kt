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
import top.fpsmaster.hud.CpsTracker
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.CPSDisplay

class CpsTextHudComponent : HudComponent(
    id = "cps_text",
    x = 10f,
    y = 28f
) {
    override fun shouldRender(): Boolean = visible && CPSDisplay.isActive()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        CPSDisplay.style.drawText(guiGraphics, resolveText(preview), 0, 0, CPSDisplay.textColorValue())
    }

    private fun resolveText(preview: Boolean): String {
        return if (preview) {
            "CPS: 7 | 5"
        } else {
            "CPS: ${CpsTracker.leftCps()} | ${CpsTracker.rightCps()}"
        }
    }
}
