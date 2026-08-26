package top.fpsmaster.module.impl.ui

import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.translation.Language

class ServerAddressDisplay : Module("server-address-display", Category.UI) {
    init {
        values.addAll(arrayOf(label, hidePort))
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
        val label = StringValue("label", "") { it.length <= 16 }

        /** Streaming-friendly: keep the host, drop the port. */
        val hidePort = OptionValue("hide-port", false)
        val textColor = HudTextColor()
        val style = HudStyle(0xA0121A1A.toInt())

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun textColorValue(): Int = textColor.argb()

        fun currentAddress(): String {
            val server = mc.currentServer ?: return Language.get("serveraddressdisplay.singleplayer")
            val address = server.ip
            if (!hidePort.getValue()) {
                return address
            }
            val separator = address.lastIndexOf(':')
            return if (separator > 0) address.substring(0, separator) else address
        }
    }
}
