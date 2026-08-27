package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
class PotionDisplay : Module("potion-display", Category.UI) {
    init {
        values.addAll(arrayOf(betterAnimation, noticeableReminder, reminderTime))
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
        val style = HudStyle()
        val betterAnimation = OptionValue("better-animation", false)
        val noticeableReminder = OptionValue("noticeable-reminder", false)
        val reminderTime = NumberValue("reminder-time", 20.0, 1.0, 120.0, 1.0) { noticeableReminder.getValue() }

        @JvmStatic
        fun durationColor(seconds: Int): Int {
            return if (noticeableReminder.getValue() && seconds <= reminderTime.getValue().toInt()) {
                0xFFFF5555.toInt()
            } else {
                0xFFCCCCCC.toInt()
            }
        }

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
