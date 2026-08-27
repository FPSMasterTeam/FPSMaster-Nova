package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class MiniMap : Module("mini-map", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                shape,
                showPlayers,
                radius
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
        // shape: 0 = square, 1 = circle (default keeps the previous circular look)
        val shape = ChoiceValue("shape", listOf("square", "circle"), "circle")
        val showPlayers = OptionValue("show-players", true)
        val radius = NumberValue("radius", 24.0, 8.0, 64.0, 1.0)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun isCircle(): Boolean = shape.isSelected("circle")
    }
}
