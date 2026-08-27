package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ChoiceValue
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
        // 0 = quality (full-resolution trail buffer), 1 = performance (960x540 trail buffer)
        @JvmField
        val mode = ChoiceValue("mode", listOf("old", "new"), "new")

        @JvmField
        val strength = NumberValue("strength", 2.0, 0.0, 10.0, 0.5)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun useFastChain(): Boolean = mode.isSelected("new")

        @JvmStatic
        fun factor(): Float {
            return (0.7f + strength.getValue().toFloat() / 100.0f * 3.0f - 0.01f)
                .coerceIn(0.0f, 0.99f)
        }
    }
}
