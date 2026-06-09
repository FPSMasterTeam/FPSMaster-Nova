package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class PlayerDisplay : Module("player-display", Category.UI) {
    init {
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
        val style = HudStyle(0x66000000)

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
