package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class BlockOverlay : Module("block-overlay", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                fill,
                fillRed,
                fillGreen,
                fillBlue,
                fillAlpha,
                outline,
                width,
                outlineRed,
                outlineGreen,
                outlineBlue,
                outlineAlpha,
                throughBlock
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
        @JvmField
        val fill = OptionValue("fill", true)

        @JvmField
        val fillRed = NumberValue("fill-red", 255.0, 0.0, 255.0, 1.0) { fill.getValue() }

        @JvmField
        val fillGreen = NumberValue("fill-green", 255.0, 0.0, 255.0, 1.0) { fill.getValue() }

        @JvmField
        val fillBlue = NumberValue("fill-blue", 255.0, 0.0, 255.0, 1.0) { fill.getValue() }

        @JvmField
        val fillAlpha = NumberValue("fill-alpha", 50.0, 0.0, 255.0, 1.0) { fill.getValue() }

        @JvmField
        val outline = OptionValue("outline", true)

        @JvmField
        val width = NumberValue("width", 1.0, 0.1, 10.0, 0.1) { outline.getValue() }

        @JvmField
        val outlineRed = NumberValue("outline-red", 255.0, 0.0, 255.0, 1.0) { outline.getValue() }

        @JvmField
        val outlineGreen = NumberValue("outline-green", 255.0, 0.0, 255.0, 1.0) { outline.getValue() }

        @JvmField
        val outlineBlue = NumberValue("outline-blue", 255.0, 0.0, 255.0, 1.0) { outline.getValue() }

        @JvmField
        val outlineAlpha = NumberValue("outline-alpha", 255.0, 0.0, 255.0, 1.0) { outline.getValue() }

        @JvmField
        val throughBlock = OptionValue("through-block", false)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun outlineColor(original: Int): Int {
            if (!active) {
                return original
            }
            if (!outline.getValue()) {
                return 0
            }

            val a = outlineAlpha.getValue().toInt().coerceIn(0, 255)
            val r = outlineRed.getValue().toInt().coerceIn(0, 255)
            val g = outlineGreen.getValue().toInt().coerceIn(0, 255)
            val b = outlineBlue.getValue().toInt().coerceIn(0, 255)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        @JvmStatic
        fun outlineWidth(original: Float): Float {
            return if (active && outline.getValue()) width.getValue().toFloat() else original
        }

        @JvmStatic
        fun shouldRenderFill(): Boolean {
            return active && fill.getValue() && fillAlpha.getValue() > 0.0
        }

        @JvmStatic
        fun fillColor(): Int {
            val a = fillAlpha.getValue().toInt().coerceIn(0, 255)
            val r = fillRed.getValue().toInt().coerceIn(0, 255)
            val g = fillGreen.getValue().toInt().coerceIn(0, 255)
            val b = fillBlue.getValue().toInt().coerceIn(0, 255)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        @JvmStatic
        fun shouldRenderThroughBlocks(): Boolean {
            return active && throughBlock.getValue()
        }
    }
}
