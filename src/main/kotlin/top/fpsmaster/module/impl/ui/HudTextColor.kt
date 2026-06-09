package top.fpsmaster.module.impl.ui

import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class HudTextColor(
    prefix: String = "text",
    redDefault: Double = 255.0,
    greenDefault: Double = 255.0,
    blueDefault: Double = 255.0,
    alphaDefault: Double = 255.0
) {
    val red = NumberValue("$prefix-red", redDefault, 0.0, 255.0, 1.0)
    val green = NumberValue("$prefix-green", greenDefault, 0.0, 255.0, 1.0)
    val blue = NumberValue("$prefix-blue", blueDefault, 0.0, 255.0, 1.0)
    val alpha = NumberValue("$prefix-alpha", alphaDefault, 0.0, 255.0, 1.0)

    fun addTo(module: Module) {
        module.values.addAll(arrayOf(red, green, blue, alpha))
    }

    fun argb(): Int {
        return (alpha.getValue().toInt().coerceIn(0, 255) shl 24) or
            (red.getValue().toInt().coerceIn(0, 255) shl 16) or
            (green.getValue().toInt().coerceIn(0, 255) shl 8) or
            blue.getValue().toInt().coerceIn(0, 255)
    }
}
