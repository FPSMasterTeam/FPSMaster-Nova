package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ColorValue

class Hitboxes : Module("hitboxes", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                color
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
        val color = ColorValue.ofRgba("color", 255.0, 255.0, 255.0, 255.0)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun colorArgb(original: Int): Int {
            if (!active) {
                return original
            }

            return color.argb()
        }
    }
}
