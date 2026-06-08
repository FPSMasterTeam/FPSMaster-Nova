package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue

class ClickGUI : Module("clickgui", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                backgroundEnabled,
                backgroundBlur,
                brandingVisible,
                animationsEnabled,
                developerMetrics,
                hardwareAcceleration,
                scale,
                width,
                height,
                commandPrefix
            )
        )
        enabled = true
    }

    companion object {
        val backgroundEnabled = OptionValue("background-enabled", true)
        val backgroundBlur = OptionValue("background-blur", true)
        val brandingVisible = OptionValue("branding-visible", true)
        val animationsEnabled = OptionValue("animations-enabled", true)
        val developerMetrics = OptionValue("developer-metrics", false)
        val hardwareAcceleration = OptionValue("hardware-acceleration", false)
        val scale = NumberValue("scale", 100.0, 50.0, 150.0, 5.0, "%")
        val width = NumberValue("width", 950.0, 720.0, 1280.0, 10.0, "px")
        val height = NumberValue("height", 620.0, 480.0, 840.0, 10.0, "px")
        val commandPrefix = StringValue(
            "command-prefix",
            ".",
            validator = { it.isNotBlank() && it.length <= 8 }
        )
    }
}
