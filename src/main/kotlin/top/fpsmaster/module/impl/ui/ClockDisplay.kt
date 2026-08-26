package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue

class ClockDisplay : Module("clock-display", Category.UI) {
    init {
        values.addAll(arrayOf(showSeconds, hour24, label))
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
        val showSeconds = OptionValue("show-seconds", true)
        val hour24 = OptionValue("hour-24", true)
        val label = StringValue("label", "") { it.length <= 16 }
        val textColor = HudTextColor()
        val style = HudStyle(0xA0121A1A.toInt())

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun textColorValue(): Int = textColor.argb()

        fun pattern(): String {
            val hours = if (hour24.getValue()) "HH" else "hh"
            val suffix = if (hour24.getValue()) "" else " a"
            return if (showSeconds.getValue()) "$hours:mm:ss$suffix" else "$hours:mm$suffix"
        }
    }
}
