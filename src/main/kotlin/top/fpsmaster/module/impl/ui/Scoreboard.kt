package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

class Scoreboard : Module("scoreboard", Category.UI) {
    init {
        style.addTo(this)
        values.add(score)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false
        val style = HudStyle(0x99000000.toInt())
        val score = OptionValue("score", false)

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun shouldShowScore(): Boolean = score.getValue()
    }
}
