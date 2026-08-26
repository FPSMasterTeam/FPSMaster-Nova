package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.ListValue

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
        val mode = ChoiceValue("mode", listOf("potions", "combat", "custom"))
        val customItems = ListValue.items(
            "items",
            listOf("minecraft:ender_pearl", "minecraft:golden_apple")
        ) { mode.isSelected("custom") }

        @JvmStatic
        fun isActive(): Boolean = active
    }
}
