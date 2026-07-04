package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.web.BasicBrowser

class ClickGUI : Module("clickgui", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                backgroundEnabled,
                brandingVisible,
                animationsEnabled,
                developerMetrics,
                hardwareAcceleration,
                width,
                height
            )
        )
        enabled = true
    }

    companion object {
        val backgroundEnabled = OptionValue("background-enabled", true)
        val brandingVisible = OptionValue("branding-visible", true)
        val animationsEnabled = OptionValue("animations-enabled", true)
        val developerMetrics = OptionValue("developer-metrics", false)
        // Default ON, but only shown when this platform + MC version can actually do GPU zero-copy
        // (Windows/Linux on a supported GPU, 1.21.5+). On unsupported setups the switch is hidden and
        // the browser stays on the CPU paint path regardless of the stored value — shouldUseAcceleration
        // gates on isAccelerationAvailable() independently, so a leftover `true` here is a harmless no-op.
        val hardwareAcceleration = OptionValue("hardware-acceleration", true) {
            BasicBrowser.isAccelerationAvailable()
        }
        val width = NumberValue("width", 950.0, 720.0, 1280.0, 10.0, "px")
        val height = NumberValue("height", 620.0, 480.0, 840.0, 10.0, "px")
    }
}
