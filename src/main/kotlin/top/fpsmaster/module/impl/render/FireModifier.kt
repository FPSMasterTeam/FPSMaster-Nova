package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class FireModifier : Module("fire-modifier", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                height,
                customColor,
                red,
                green,
                blue
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
        @JvmField
        val height = NumberValue("height", 0.2, 0.0, 0.7, 0.1)

        @JvmField
        val customColor = OptionValue("custom-color", false)

        @JvmField
        val red = NumberValue("red", 255.0, 0.0, 255.0, 1.0) { customColor.getValue() }

        @JvmField
        val green = NumberValue("green", 0.0, 0.0, 255.0, 1.0) { customColor.getValue() }

        @JvmField
        val blue = NumberValue("blue", 0.0, 0.0, 255.0, 1.0) { customColor.getValue() }

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun adjustedY(value: Float): Float {
            return if (active) value - height.getValue().toFloat() else value
        }

        @JvmStatic
        fun useCustomColor(): Boolean = active && customColor.getValue()

        @JvmStatic
        fun red(): Float = red.getValue().toFloat() / 255.0f

        @JvmStatic
        fun green(): Float = green.getValue().toFloat() / 255.0f

        @JvmStatic
        fun blue(): Float = blue.getValue().toFloat() / 255.0f
    }
}
