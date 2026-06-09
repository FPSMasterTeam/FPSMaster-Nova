package top.fpsmaster.module.impl.auxiliary

import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class RawInput : Module("raw-input", Category.AUXILIARY) {
    override fun onEnable() {
        previousValue = mc.options.rawMouseInput().get()
        mc.options.rawMouseInput().set(true)
    }

    override fun onDisable() {
        previousValue?.let { mc.options.rawMouseInput().set(it) }
        previousValue = null
    }

    companion object {
        private var previousValue: Boolean? = null
    }
}
