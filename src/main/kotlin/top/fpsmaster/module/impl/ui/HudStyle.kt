package top.fpsmaster.module.impl.ui

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import top.fpsmaster.mc
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.render.font.Fonts
import top.fpsmaster.ui.kit.NovaShapeAtlas

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
        module.values.addAll(arrayOf(rounded, roundRadius, spacing, betterFont, fontShadow, background))
        backgroundColor.addTo(module, "background")
    }

    fun component(text: String): Component {
        return if (betterFont.getValue()) {
            Component.literal(text).withStyle(Style.EMPTY.withFont(Fonts.fontJetBrainsMono10))
        } else {
            Component.literal(text)
        }
    }

    fun component(text: Component): Component {
        return if (betterFont.getValue()) {
            text.copy().withStyle(Style.EMPTY.withFont(Fonts.fontJetBrainsMono10))
        } else {
            text
        }
    }

    fun width(text: String): Int = mc.font.width(component(text))

    fun drawText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int) {
        val label = component(text)
        fillBackground(guiGraphics, x - 2, y, x + mc.font.width(label) + 2, y + mc.font.lineHeight)
        guiGraphics.drawString(mc.font, label, x, y, color, fontShadow.getValue())
    }

    fun fillBackground(guiGraphics: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int) {
        if (!background.getValue()) {
            return
        }
        val backgroundArgb = backgroundColor.argb()
        if ((backgroundArgb ushr 24) == 0) {
            return
        }
        if (rounded.getValue()) {
            val w = (right - left).toFloat()
            val h = (bottom - top).toFloat()
            if (NovaShapeAtlas.fillRoundRect(
                    guiGraphics,
                    left.toFloat(),
                    top.toFloat(),
                    w,
                    h,
                    roundRadius.getValue().toFloat(),
                    backgroundArgb
                )
            ) {
                return
            }
        }
        guiGraphics.fill(left, top, right, bottom, backgroundArgb)
    }

    fun lineStep(base: Int): Int {
        return base + spacing.getValue().toInt()
    }
}
