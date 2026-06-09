package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class MotionBlur : Module("motion-blur", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                mode,
                strength
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
        val mode = NumberValue("mode", 1.0, 0.0, 1.0, 1.0)

        @JvmField
        val strength = NumberValue("strength", 2.0, 0.0, 10.0, 0.5)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun factor(): Float {
            return (0.7f + strength.getValue().toFloat() / 100.0f * 3.0f - 0.01f)
                .coerceIn(0.0f, 0.99f)
        }
    }
}
