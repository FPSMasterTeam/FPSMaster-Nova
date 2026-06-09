package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class BlockIndicator : Module("block-indicator", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                showId,
                showCoords,
                yOffset,
                backgroundRed,
                backgroundGreen,
                backgroundBlue,
                backgroundAlpha,
                panelRed,
                panelGreen,
                panelBlue,
                panelAlpha,
                accentRed,
                accentGreen,
                accentBlue,
                accentAlpha
            )
        )
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false
        val showId = OptionValue("show-id", true)
        val showCoords = OptionValue("show-coords", true)
        val yOffset = NumberValue("y-offset", 18.0, 0.0, 120.0, 1.0)
        val backgroundRed = NumberValue("background-red", 18.0, 0.0, 255.0, 1.0)
        val backgroundGreen = NumberValue("background-green", 20.0, 0.0, 255.0, 1.0)
        val backgroundBlue = NumberValue("background-blue", 26.0, 0.0, 255.0, 1.0)
        val backgroundAlpha = NumberValue("background-alpha", 190.0, 0.0, 255.0, 1.0)
        val panelRed = NumberValue("panel-red", 255.0, 0.0, 255.0, 1.0)
        val panelGreen = NumberValue("panel-green", 255.0, 0.0, 255.0, 1.0)
        val panelBlue = NumberValue("panel-blue", 255.0, 0.0, 255.0, 1.0)
        val panelAlpha = NumberValue("panel-alpha", 24.0, 0.0, 255.0, 1.0)
        val accentRed = NumberValue("accent-red", 105.0, 0.0, 255.0, 1.0)
        val accentGreen = NumberValue("accent-green", 180.0, 0.0, 255.0, 1.0)
        val accentBlue = NumberValue("accent-blue", 255.0, 0.0, 255.0, 1.0)
        val accentAlpha = NumberValue("accent-alpha", 220.0, 0.0, 255.0, 1.0)

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun yOffsetValue(): Float = yOffset.getValue().toFloat()

        @JvmStatic
        fun backgroundColor(): Int {
            return argb(backgroundAlpha.getValue(), backgroundRed.getValue(), backgroundGreen.getValue(), backgroundBlue.getValue())
        }

        @JvmStatic
        fun panelColor(): Int {
            return argb(panelAlpha.getValue(), panelRed.getValue(), panelGreen.getValue(), panelBlue.getValue())
        }

        @JvmStatic
        fun accentColor(): Int {
            return argb(accentAlpha.getValue(), accentRed.getValue(), accentGreen.getValue(), accentBlue.getValue())
        }

        private fun argb(alpha: Double, red: Double, green: Double, blue: Double): Int {
            return (alpha.toInt().coerceIn(0, 255) shl 24) or
                (red.toInt().coerceIn(0, 255) shl 16) or
                (green.toInt().coerceIn(0, 255) shl 8) or
                blue.toInt().coerceIn(0, 255)
        }
    }
}
