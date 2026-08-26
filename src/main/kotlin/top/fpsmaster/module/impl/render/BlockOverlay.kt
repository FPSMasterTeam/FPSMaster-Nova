package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ColorValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class BlockOverlay : Module("block-overlay", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                fill,
                fillTint,
                outline,
                width,
                outlineTint,
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
        val fillTint = ColorValue.ofRgba("fill-color", 255.0, 255.0, 255.0, 50.0) { fill.getValue() }

        @JvmField
        val outline = OptionValue("outline", true)

        @JvmField
        val width = NumberValue("width", 1.0, 0.1, 10.0, 0.1) { outline.getValue() }

        @JvmField
        val outlineTint = ColorValue.ofRgba("outline-color", 255.0, 255.0, 255.0, 255.0) { outline.getValue() }

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

            return outlineTint.argb()
        }

        @JvmStatic
        fun outlineWidth(original: Float): Float {
            return if (active && outline.getValue()) width.getValue().toFloat() else original
        }

        @JvmStatic
        fun shouldRenderFill(): Boolean {
            return active && fill.getValue() && fillTint.alphaF() > 0f
        }

        @JvmStatic
        fun fillColor(): Int {
            return fillTint.argb()
        }

        @JvmStatic
        fun shouldRenderThroughBlocks(): Boolean {
            return active && throughBlock.getValue()
        }
    }
}
