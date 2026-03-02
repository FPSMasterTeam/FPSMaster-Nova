package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.util.function.Supplier

class NumberValue(identity: String, value: Double, val minimum: Double, val maximum: Double, val increment: Double, val unit: String = "", displayable: Supplier<Boolean> = Supplier {true}) : Value<Double>(identity, value, displayable) {
    override fun setValue(value: Double) {
        if (value < minimum) {
            super.setValue(minimum)
        } else if (value > maximum) {
            super.setValue(maximum)
        } else {
            val rest = value % increment
            if (rest < 0.5) {
                super.setValue(value - rest)
            } else {
                super.setValue(value + increment - rest)
            }
        }
    }
}