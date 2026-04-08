package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class ClickGUI : Module("clickgui", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                backgroundEnabled,
                backgroundBlur,
                brandingVisible,
                animationsEnabled,
                width,
                height
            )
        )
        enabled = true
    }

    companion object {
        val backgroundEnabled = OptionValue("background_enabled", true)
        val backgroundBlur = OptionValue("background_blur", true)
        val brandingVisible = OptionValue("branding_visible", true)
        val animationsEnabled = OptionValue("animations_enabled", true)
        val width = NumberValue("width", 950.0, 720.0, 1280.0, 10.0, "px")
        val height = NumberValue("height", 620.0, 480.0, 840.0, 10.0, "px")
    }
}
