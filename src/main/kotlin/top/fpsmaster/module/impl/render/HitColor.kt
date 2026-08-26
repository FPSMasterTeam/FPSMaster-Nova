package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ColorValue

class HitColor : Module("hit-color", Category.RENDER) {
    init {
        values.add(color)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        @JvmField
        val color = ColorValue.ofRgba("color", 255.0, 0.0, 0.0, 120.0)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun colorArgb(): Int = color.argb()
    }
}
