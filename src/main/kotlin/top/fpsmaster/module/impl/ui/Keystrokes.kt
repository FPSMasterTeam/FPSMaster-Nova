package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class Keystrokes : Module("keystrokes", Category.UI) {
    init {
        style.roundRadius.setValue(2.0)
        values.addAll(
            arrayOf(
                borderWidth,
                pressAnimMode,
                pressAnimDuration,
                showSpace,
                cpsMode,
                wasdStyle,
                spaceStyle
            )
        )
        style.addTo(this)
        pressedColor.addTo(this)
        fontColor.addTo(this)
        pressedFontColor.addTo(this)
        borderColor.addTo(this)
        pressAnimColor.addTo(this)
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
        val pressedColor = HudTextColor("pressed", 255.0, 255.0, 255.0, 120.0)
        val fontColor = HudTextColor("font", 255.0, 255.0, 255.0, 255.0)
        val pressedFontColor = HudTextColor("pressed-font", 201.0, 201.0, 201.0, 255.0)
        val borderWidth = NumberValue("border-width", 1.0, 0.0, 4.0, 0.5)
        val borderColor = HudTextColor("border", 255.0, 255.0, 255.0, 80.0)
        val pressAnimMode = ChoiceValue("press-anim-mode", listOf("color", "pulse", "ripple", "bloom", "stack"))
        val pressAnimColor = HudTextColor("press-anim", 255.0, 255.0, 255.0, 120.0)
        val pressAnimDuration = NumberValue("press-anim-duration", 0.25, 0.05, 1.0, 0.05)
        val showSpace = OptionValue("show-space", true)
        val cpsMode = ChoiceValue("cps-mode", listOf("below", "clickonly", "off"))
        val wasdStyle = ChoiceValue("wasd-style", listOf("text", "triangle"))
        val spaceStyle = ChoiceValue("space-style", listOf("text", "bar"))

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun backgroundColor(): Int = style.backgroundColor.argb()

        @JvmStatic
        fun pressedColor(): Int = pressedColor.argb()

        @JvmStatic
        fun fontColor(pressed: Boolean): Int = if (pressed) pressedFontColor.argb() else fontColor.argb()

        @JvmStatic
        fun borderColor(): Int = borderColor.argb()

        @JvmStatic
        fun shouldDrawBackground(): Boolean = style.background.getValue()

        @JvmStatic
        fun spacing(): Int = style.spacing.getValue().toInt()

        @JvmStatic
        fun cpsModeIndex(): Int = cpsMode.index

        @JvmStatic
        fun wasdStyleIndex(): Int = wasdStyle.index

        @JvmStatic
        fun spaceStyleIndex(): Int = spaceStyle.index
    }
}
