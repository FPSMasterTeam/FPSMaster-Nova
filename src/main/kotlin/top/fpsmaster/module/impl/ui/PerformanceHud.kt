package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

/**
 * The diagnostic frame-rate overlay: trace, distribution, heap and collections.
 *
 * Distinct from [FPSDisplay], which is one number in a corner and should stay that way. This is the
 * view to have open while judging whether a setting helped, because a frame rate cannot answer that
 * on its own. Each row is a switch, so it can sit at one line while playing and open up while testing.
 */
class PerformanceHud : Module("performance-hud", Category.UI) {
    init {
        values.addAll(arrayOf(showGraph, showDistribution, showMemory, showGarbageCollection, colorByHealth))
        textColor.addTo(this)
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

        /** Six seconds of frame history, worst frame per column. */
        val showGraph = OptionValue("show-graph", true)

        /** Average, 1% low, median, worst, and the hitch count. */
        val showDistribution = OptionValue("show-distribution", true)

        /** Heap in use against the maximum, and the client thread's allocation rate. */
        val showMemory = OptionValue("show-memory", true)

        /** Collections a second, and the milliseconds a second they stop the game for. */
        val showGarbageCollection = OptionValue("show-gc", true)

        /**
         * Colours the frame rate and the 1% low by how good they are. The thresholds are about this
         * game rather than about video: 60 is where the frame stops keeping up with a 20-tick
         * server's interpolation, 30 is where aim starts to suffer.
         */
        val colorByHealth = OptionValue("color-by-health", true)

        val textColor = HudTextColor()
        val style = HudStyle(0xB0121A1A.toInt())

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun textColorValue(): Int = textColor.argb()

        /** Green above 60fps, amber above 30, red below. */
        fun healthColor(framesPerSecond: Double): Int = when {
            !colorByHealth.getValue() -> textColor.argb()
            framesPerSecond >= 60.0 -> 0xFF5BD97F.toInt()
            framesPerSecond >= 30.0 -> 0xFFE9C46A.toInt()
            else -> 0xFFE76F51.toInt()
        }
    }
}
