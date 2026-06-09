package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.impl.ui.HudTextColor
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import java.awt.Color

class DragonWings : Module("dragon-wings", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                colored,
                chroma,
                scale
            )
        )
        color.addTo(this)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        val colored = OptionValue("colored", false)
        val chroma = OptionValue("chroma", false)
        val scale = NumberValue("scale", 100.0, 0.0, 100.0, 1.0, "%")
        val color = HudTextColor("color", 0.0, 0.0, 0.0, 255.0)
        private var active = false
        private var renderingForcedWings = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun scaleMultiplier(): Float = (scale.getValue() / 100.0).toFloat()

        @JvmStatic
        fun beginForcedWingsRender() {
            renderingForcedWings = true
        }

        @JvmStatic
        fun endForcedWingsRender() {
            renderingForcedWings = false
        }

        @JvmStatic
        fun wingTintColor(original: Int): Int {
            if (!renderingForcedWings || !colored.getValue()) {
                return original
            }
            if (chroma.getValue()) {
                return Color.HSBtoRGB((System.currentTimeMillis() % 1000L) / 1000.0f, 0.8f, 1.0f) or 0xFF000000.toInt()
            }
            return color.argb()
        }

        @JvmStatic
        fun renderColor(): Int {
            if (!colored.getValue()) {
                return 0xFFFFFFFF.toInt()
            }
            if (chroma.getValue()) {
                return Color.HSBtoRGB((System.currentTimeMillis() % 1000L) / 1000.0f, 0.8f, 1.0f) or 0xFF000000.toInt()
            }
            return color.argb()
        }
    }
}
