package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

class BetterScreen : Module("better-screen", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                background,
                blur,
                backgroundAnimation,
                noFlickering
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
        @JvmField
        val background = OptionValue("background", true)

        @JvmField
        val blur = OptionValue("blur", true)

        @JvmField
        val backgroundAnimation = OptionValue("background-animation", true)

        @JvmField
        val noFlickering = OptionValue("no-flickering", true)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
