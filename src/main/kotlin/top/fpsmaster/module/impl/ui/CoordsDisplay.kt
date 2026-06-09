package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class CoordsDisplay : Module("coords-display", Category.UI) {
    init {
        style.addTo(this)
        values.addAll(
            arrayOf(
                limitDisplay,
                limitDisplayY
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
        private var active = false
        val style = HudStyle()
        val limitDisplay = OptionValue("limit-display", false)
        val limitDisplayY = NumberValue("limit-display-y", 92.0, 0.0, 320.0, 1.0)

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
