package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

class TabOverlay : Module("tab-overlay", Category.UI) {
    init {
        values.add(showPing)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false
        val showPing = OptionValue("show-ping", true)

        @JvmStatic
        fun shouldOverridePing(): Boolean = active

        @JvmStatic
        fun shouldShowPingText(): Boolean = active && showPing.getValue()
    }
}
