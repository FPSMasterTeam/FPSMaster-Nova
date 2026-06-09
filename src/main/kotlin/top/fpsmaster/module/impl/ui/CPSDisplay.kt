package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class CPSDisplay : Module("cps-display", Category.UI) {
    init {
        textColor.addTo(this)
        style.addTo(this)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false
        val textColor = HudTextColor()
        val style = HudStyle()

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun textColorValue(): Int = textColor.argb()
    }
}
