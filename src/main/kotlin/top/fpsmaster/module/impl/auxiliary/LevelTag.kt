package top.fpsmaster.module.impl.auxiliary

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.impl.ui.HudTextColor
import top.fpsmaster.module.value.impl.OptionValue

class LevelTag : Module("level-tag", Category.AUXILIARY) {
    init {
        values.addAll(
            arrayOf(
                showSelf,
                health,
                disableBackground
            )
        )
        backgroundColor.addTo(this)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false
        val showSelf = OptionValue("show-self", true)
        val health = OptionValue("health", true)
        val disableBackground = OptionValue("disable-background", false)
        val backgroundColor = HudTextColor("background", 0.0, 0.0, 0.0, 50.0)

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun shouldShowSelfNameTag(): Boolean = active && showSelf.getValue()

        @JvmStatic
        fun nameTagBackgroundColor(original: Int): Int {
            if (!active || original == 0) {
                return original
            }
            if (disableBackground.getValue()) {
                return 0
            }
            return backgroundColor.argb()
        }
    }
}
