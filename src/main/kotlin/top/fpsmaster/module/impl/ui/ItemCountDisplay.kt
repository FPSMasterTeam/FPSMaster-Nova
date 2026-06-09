package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.StringValue

class ItemCountDisplay : Module("item-count-display", Category.UI) {
    init {
        style.addTo(this)
        values.addAll(
            arrayOf(
                mode,
                customItems
            )
        )
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false
        val style = HudStyle(0x66000000)
        val mode = NumberValue("mode", 0.0, 0.0, 2.0, 1.0)
        val customItems = StringValue("items", "minecraft:ender_pearl,minecraft:golden_apple")

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
