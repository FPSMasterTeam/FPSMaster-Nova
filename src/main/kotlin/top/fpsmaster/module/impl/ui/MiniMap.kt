package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class MiniMap : Module("mini-map", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                showPlayers,
                radius
            )
        )
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        val showPlayers = OptionValue("show-players", true)
        val radius = NumberValue("radius", 24.0, 8.0, 64.0, 1.0)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
