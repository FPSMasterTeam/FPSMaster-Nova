package top.fpsmaster.module.impl.ui

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.mc
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class HudStyle(defaultBackgroundColor: Int = 0x00000000) {
    val rounded = OptionValue("round", true)
    val roundRadius = NumberValue("round-radius", 3.0, 0.0, 30.0, 1.0) { rounded.getValue() }
    val betterFont = OptionValue("better-font", false)
    val fontShadow = OptionValue("font-shadow", true)
    val background = OptionValue("background", true)
    val backgroundColor = HudTextColor(
        prefix = "background",
        redDefault = ((defaultBackgroundColor shr 16) and 0xFF).toDouble(),
        greenDefault = ((defaultBackgroundColor shr 8) and 0xFF).toDouble(),
        blueDefault = (defaultBackgroundColor and 0xFF).toDouble(),
        alphaDefault = ((defaultBackgroundColor ushr 24) and 0xFF).toDouble()
    )
    val spacing = NumberValue("spacing", 0.0, 0.0, 3.0, 1.0)

    fun addTo(module: Module) {
        // Shared, reusable groups: any module importing HudStyle gets these collapsible sections for free.
        rounded.inGroup("style"); roundRadius.inGroup("style"); spacing.inGroup("style")
        betterFont.inGroup("font"); fontShadow.inGroup("font")
        background.inGroup("background")
        module.values.addAll(arrayOf(rounded, roundRadius, betterFont, fontShadow, background, spacing))
        backgroundColor.addTo(module, "background")
    }

    fun drawText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int) {
        fillBackground(guiGraphics, x - 2, y, x + mc.font.width(text) + 2, y + mc.font.lineHeight)

        guiGraphics.drawString(mc.font, text, x, y, color, fontShadow.getValue())
    }

    fun fillBackground(guiGraphics: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int) {
        if (!background.getValue()) {
            return
        }

        val backgroundArgb = backgroundColor.argb()
        if ((backgroundArgb ushr 24) > 0) {
            guiGraphics.fill(left, top, right, bottom, backgroundArgb)
        }
    }

    fun lineStep(base: Int): Int {
        return base + spacing.getValue().toInt()
    }
}
