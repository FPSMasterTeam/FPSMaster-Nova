package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class PotionDisplay : Module("potion-display", Category.UI) {
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
        val style = HudStyle()

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
