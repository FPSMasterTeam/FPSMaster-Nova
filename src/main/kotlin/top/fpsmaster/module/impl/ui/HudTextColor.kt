package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ColorValue

/**
 * Reusable colour block for HUD text and panels. Callers keep declaring defaults as 0-255 channels,
 * but the setting itself is a single [ColorValue], so the ClickGUI shows one picker with animation modes
 * instead of four sliders.
 */
class HudTextColor(
    prefix: String = "text",
    redDefault: Double = 255.0,
    greenDefault: Double = 255.0,
    blueDefault: Double = 255.0,
    alphaDefault: Double = 255.0
) {
    val color = ColorValue.ofRgba("$prefix-color", redDefault, greenDefault, blueDefault, alphaDefault)

    /** Add the colour to [module], optionally inside a collapsible [group] (e.g. "background"). */
    fun addTo(module: Module, group: String? = null) {
        if (group != null) {
            color.inGroup(group)
        }
        module.values.add(color)
    }

    /** Resolved ARGB, including the selected animation. [offset] shifts animations per element. */
    fun argb(offset: Float = 0f): Int = color.argb(offset)
}
