package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue

class ModsList : Module("mods-list", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                showText,
                text,
                english,
                rainbow
            )
        )
        color.addTo(this)
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
        val showText = OptionValue("show-text", true)
        val english = OptionValue("english", true)
        val rainbow = OptionValue("rainbow", true)
        val text = StringValue("text", "FPSMaster")
        val color = HudTextColor("color")
        val style = HudStyle()

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun shouldShowText(): Boolean = showText.getValue()

        @JvmStatic
        fun shouldUseEnglish(): Boolean = english.getValue()

        @JvmStatic
        fun shouldUseRainbow(): Boolean = rainbow.getValue()

        @JvmStatic
        fun titleText(): String = text.getValue()

        @JvmStatic
        fun colorValue(): Int = color.argb()
    }
}
