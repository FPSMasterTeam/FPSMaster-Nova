package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class LyricsDisplay : Module("lyrics-display", Category.UI) {
    init {
        background.inGroup("background")
        values.add(background)
        backgroundColor.addTo(this, "background")
        fontSize.inGroup("font")
        values.add(fontSize)
        textColor.addTo(this, "font")
        scroll.inGroup("style")
        lines.inGroup("style")
        translation.inGroup("style")
        values.addAll(arrayOf(scroll, lines, translation))
    }

    override fun onEnable() { active = true }
    override fun onDisable() { active = false }

    companion object {
        private var active = false
        val background = OptionValue("background", true)
        val backgroundColor = HudTextColor("panel", 0.0, 0.0, 0.0, 150.0)
        val fontSize = NumberValue("font-size", 18.0, 10.0, 30.0, 1.0)
        val scroll = OptionValue("scroll", true)
        val lines = NumberValue("lines", 2.0, 1.0, 5.0, 1.0)
        val translation = OptionValue("translation", true)
        val textColor = HudTextColor("text")

        @JvmStatic fun isActive(): Boolean = active
    }
}
