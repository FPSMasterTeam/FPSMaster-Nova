package top.fpsmaster.hud.impl

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.FPSDisplay

class FpsTextHudComponent : HudComponent(
    id = "fps_text",
    x = 10f,
    y = 46f
) {
    override fun shouldRender(): Boolean = visible && FPSDisplay.isActive()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        FPSDisplay.style.drawText(guiGraphics, resolveText(preview), 0, 0, FPSDisplay.textColorValue())
    }

    private fun resolveText(preview: Boolean): String {
        return if (preview) {
            "240fps"
        } else {
            //? if >=1.20 {
            "${mc.fps}fps"
            //?} else {
            /*"${net.minecraft.client.Minecraft.fps}fps"*/
            //?}
        }
    }
}
