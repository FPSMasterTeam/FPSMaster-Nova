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
import top.fpsmaster.module.impl.ui.ClockDisplay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ClockTextHudComponent : HudComponent(
    id = "clock",
    x = 10f,
    y = 182f
) {
    private var pattern = ""
    private var formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)

    override fun shouldRender(): Boolean = visible && ClockDisplay.isActive()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        ClockDisplay.style.drawText(guiGraphics, resolveText(preview), 0, 0, ClockDisplay.textColorValue())
    }

    private fun resolveText(preview: Boolean): String {
        val label = ClockDisplay.label.getValue().trim()
        val time = if (preview) "12:34:56" else formatter().format(LocalTime.now())
        return if (label.isEmpty()) time else "$label $time"
    }

    /** Rebuilt only when the pattern changes; parsing one per frame is pure waste. */
    private fun formatter(): DateTimeFormatter {
        val current = ClockDisplay.pattern()
        if (current != pattern) {
            pattern = current
            formatter = DateTimeFormatter.ofPattern(current, Locale.ROOT)
        }
        return formatter
    }
}
