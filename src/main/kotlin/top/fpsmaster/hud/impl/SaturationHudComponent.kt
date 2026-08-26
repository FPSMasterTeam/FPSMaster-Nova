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
import top.fpsmaster.module.impl.ui.SaturationDisplay
import kotlin.math.roundToInt

class SaturationHudComponent : HudComponent(
    id = "saturation",
    x = 10f,
    y = 218f
) {
    override fun shouldRender(): Boolean = visible && SaturationDisplay.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val width = SaturationDisplay.barWidth.getValue().toFloat() +
            if (SaturationDisplay.showValue.getValue()) VALUE_GAP + mc.font.width(valueText(preview)) else 0
        return HudSize(
            width = width.coerceAtLeast(1f),
            height = maxOf(BAR_HEIGHT, mc.font.lineHeight).toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val barWidth = SaturationDisplay.barWidth.getValue().toInt()
        val filled = (barWidth * fraction(preview)).roundToInt().coerceIn(0, barWidth)
        // Centre the bar against the text: the number is taller than the bar and drives the row height.
        val barTop = (mc.font.lineHeight - BAR_HEIGHT) / 2

        SaturationDisplay.style.fillBackground(guiGraphics, -2, 0, barWidth + 2, mc.font.lineHeight)
        guiGraphics.fill(0, barTop, barWidth, barTop + BAR_HEIGHT, TRACK_COLOR)
        if (filled > 0) {
            guiGraphics.fill(0, barTop, filled, barTop + BAR_HEIGHT, SaturationDisplay.barColorValue())
        }

        if (SaturationDisplay.showValue.getValue()) {
            guiGraphics.drawString(
                mc.font,
                valueText(preview),
                barWidth + VALUE_GAP,
                0,
                SaturationDisplay.textColorValue(),
                SaturationDisplay.style.fontShadow.getValue()
            )
        }
    }

    private fun fraction(preview: Boolean): Float =
        (saturation(preview) / SaturationDisplay.MAX_SATURATION).coerceIn(0f, 1f)

    private fun saturation(preview: Boolean): Float =
        if (preview) 14.5f else SaturationDisplay.currentSaturation()

    /** Halves matter — eating one steak past 17.5 is wasted, and only the decimal shows that. */
    private fun valueText(preview: Boolean): String {
        val saturation = saturation(preview)
        val rounded = (saturation * 10).roundToInt() / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    companion object {
        private const val BAR_HEIGHT = 4
        private const val VALUE_GAP = 4
        private const val TRACK_COLOR = 0x66000000
    }
}
