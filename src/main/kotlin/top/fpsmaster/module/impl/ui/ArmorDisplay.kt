package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class ArmorDisplay : Module("armor-display", Category.UI) {
    init {
        style.addTo(this)
        values.add(mode)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false
        val style = HudStyle(0x66000000)
        val mode = NumberValue("mode", 0.0, 0.0, 2.0, 1.0)

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun modeIndex(): Int = mode.getValue().toInt()
    }
}
