package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class WavyCape : Module("wavy-cape", Category.RENDER) {
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
