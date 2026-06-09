package top.fpsmaster.module.impl.optimization

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class FixedInventory : Module("fixed-inventory", Category.OPTIMIZATION) {
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
