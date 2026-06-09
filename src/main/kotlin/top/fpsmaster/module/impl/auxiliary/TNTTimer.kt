package top.fpsmaster.module.impl.auxiliary

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class TNTTimer : Module("tnt-timer", Category.AUXILIARY) {
    init {
        values.add(duration)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false
        val duration = NumberValue("duration", 4.0, 1.0, 10.0, 0.1)

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
