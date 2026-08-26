package top.fpsmaster.module.impl.ui

import io.github.vlouboos.standaloneevent.api.EventHandler
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.statistics.PlayTimeStatistics

/** Time played on the current server (or world): this session, today, or all of it. */
class PlayTime : Module("play-time", Category.UI) {
    init {
        values.addAll(arrayOf(displayMode, label))
        textColor.addTo(this)
        style.addTo(this)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        PlayTimeStatistics.flush()
    }

    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        PlayTimeStatistics.update()
    }

    companion object {
        private var active = false

        /** 0 session, 1 today, 2 total. */
        val displayMode = NumberValue("display-mode", 0.0, 0.0, 2.0, 1.0)
        val label = StringValue("label", "") { it.length <= 16 }
        val textColor = HudTextColor()
        val style = HudStyle(0xA0121A1A.toInt())

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun textColorValue(): Int = textColor.argb()

        fun currentText(): String = PlayTimeStatistics.format(
            PlayTimeStatistics.displayMillis(displayMode.getValue().toInt())
        )
    }
}
