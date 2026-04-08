package top.fpsmaster.module.impl.optimization

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class BetterFishingRod : Module("betterfishingrod", Category.OPTIMIZATION) {
    init {
        values.add(stringWidth)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        @JvmField
        val stringWidth = NumberValue("string_width", 1.0, 1.0, 10.0, 0.5)

        private var active = false

        @JvmStatic
        fun resolveLineWidth(fallback: Float): Float {
            return if (active) stringWidth.getValue().toFloat() else fallback
        }
    }
}
