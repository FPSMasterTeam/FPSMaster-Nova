package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.util.function.Supplier
import kotlin.math.roundToLong

class NumberValue(identity: String, value: Double, val minimum: Double, val maximum: Double, val increment: Double, val unit: String = "", displayable: Supplier<Boolean> = Supplier {true}) : Value<Double>(identity, value, displayable) {
    /**
     * Snaps to the nearest reachable step, counting from [minimum] rather than from zero, then clamps.
     * Anchoring on [minimum] is what makes negative or non-multiple minimums land on values the slider
     * can actually reproduce (min -0.5, increment 0.25 keeps -0.25, not 0.0).
     */
    override fun setValue(value: Double) {
        val clamped = value.coerceIn(minimum, maximum)
        if (increment <= 0.0) {
            super.setValue(clamped)
            return
        }
        val steps = ((clamped - minimum) / increment).roundToLong()
        val snapped = minimum + steps * increment
        // Binary residue from step arithmetic (0.1 * 3 = 0.30000000000000004) would leak into the
        // config file and the slider label; six decimals is far finer than any setting we expose.
        val trimmed = (snapped * 1_000_000.0).roundToLong() / 1_000_000.0
        super.setValue(trimmed.coerceIn(minimum, maximum))
    }
}
