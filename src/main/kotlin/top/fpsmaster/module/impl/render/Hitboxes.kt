package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class Hitboxes : Module("hitboxes", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                red,
                green,
                blue,
                alpha
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
        val red = NumberValue("red", 255.0, 0.0, 255.0, 1.0)

        @JvmField
        val green = NumberValue("green", 255.0, 0.0, 255.0, 1.0)

        @JvmField
        val blue = NumberValue("blue", 255.0, 0.0, 255.0, 1.0)

        @JvmField
        val alpha = NumberValue("alpha", 255.0, 0.0, 255.0, 1.0)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun colorArgb(original: Int): Int {
            if (!active) {
                return original
            }

            val a = alpha.getValue().toInt().coerceIn(0, 255)
            val r = red.getValue().toInt().coerceIn(0, 255)
            val g = green.getValue().toInt().coerceIn(0, 255)
            val b = blue.getValue().toInt().coerceIn(0, 255)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
