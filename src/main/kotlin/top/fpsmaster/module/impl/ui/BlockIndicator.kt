package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ColorValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class BlockIndicator : Module("block-indicator", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                showId,
                showCoords,
                yOffset,
                backgroundColor,
                panelColor,
                accentColor
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
        val backgroundColor = ColorValue.ofRgba("background-color", 18.0, 20.0, 26.0, 190.0)
        val panelColor = ColorValue.ofRgba("panel-color", 255.0, 255.0, 255.0, 24.0)
        val accentColor = ColorValue.ofRgba("accent-color", 105.0, 180.0, 255.0, 220.0)

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun yOffsetValue(): Float = yOffset.getValue().toFloat()

        @JvmStatic
        fun backgroundColor(): Int = backgroundColor.argb()

        @JvmStatic
        fun panelColor(): Int = panelColor.argb()

        @JvmStatic
        fun accentColor(): Int = accentColor.argb()
    }
}
