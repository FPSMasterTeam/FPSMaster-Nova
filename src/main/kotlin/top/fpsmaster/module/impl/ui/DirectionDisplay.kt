package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class DirectionDisplay : Module("direction-display", Category.UI) {
    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
