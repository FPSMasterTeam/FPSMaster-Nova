package top.fpsmaster.module.impl.ui

import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

/**
 * Shows saturation, which the vanilla HUD hides entirely even though it decides when the hunger bar
 * starts dropping. Rendered as its own bar plus a number; the vanilla food bar is untouched.
 */
class SaturationDisplay : Module("saturation-display", Category.UI) {
    init {
        values.addAll(arrayOf(showValue, barWidth))
        barColor.addTo(this, "colors")
        textColor.addTo(this, "colors")
        style.addTo(this)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        /** Vanilla caps saturation at the food level, which itself caps at 20. */
        const val MAX_SATURATION = 20f

        private var active = false
        val showValue = OptionValue("show-value", true)
        val barWidth = NumberValue("bar-width", 60.0, 20.0, 120.0, 2.0, "px")
        val barColor = HudTextColor(prefix = "bar", redDefault = 255.0, greenDefault = 190.0, blueDefault = 60.0)
        val textColor = HudTextColor()
        val style = HudStyle(0xA0121A1A.toInt())

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun barColorValue(): Int = barColor.argb()

        @JvmStatic
        fun textColorValue(): Int = textColor.argb()

        fun currentSaturation(): Float = mc.player?.foodData?.saturationLevel ?: 0f
    }
}
