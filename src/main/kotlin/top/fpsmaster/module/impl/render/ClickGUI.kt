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
        // Default OFF (opt-in), matching upstream CCBlueX/mcef which ships accelerated paint as beta.
        // The zero-copy path is the fast one, but it's the less-tested rendering path — users who want
        // it turn it on. Shown only where zero-copy can actually work (Windows/Linux on a supported GPU,
        // 1.21.5+); on unsupported setups the switch is hidden and the browser stays on the CPU path
        // regardless of the stored value — shouldUseAcceleration gates on isAccelerationAvailable().
        // Visibility keys off platform SUPPORT (not session availability): turning the toggle on only
        // takes effect after a restart (the CEF pump mode is fixed at init), and gating visibility on
        // availability would hide the switch whenever it's off — making it impossible to enable.
        val hardwareAcceleration = OptionValue("hardware-acceleration", false) {
            BasicBrowser.isAccelerationSupported()
        }
        val width = NumberValue("width", 950.0, 720.0, 1280.0, 10.0, "px")
        val height = NumberValue("height", 620.0, 480.0, 840.0, 10.0, "px")
    }
}
