package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class CustomTitles : Module("custom-titles", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                x,
                y,
                scale
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
        val x = NumberValue("x", 0.0, -500.0, 500.0, 1.0)
        val y = NumberValue("y", 0.0, -500.0, 500.0, 1.0)
        val scale = NumberValue("scale", 1.0, 0.0, 3.0, 0.02)

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun xOffset(): Float = if (active) x.getValue().toFloat() else 0f

        @JvmStatic
        fun yOffset(): Float = if (active) y.getValue().toFloat() else 0f

        @JvmStatic
        fun scaleValue(): Float = if (active) scale.getValue().toFloat() else 1f
    }
}
