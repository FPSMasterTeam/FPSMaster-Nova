package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

class NumberValue(identity: String, value: Double, val minimum: Double, val maximum: Double, displayable: Supplier<Boolean> = Supplier {true}) : Value<Double>(identity, value, displayable) {
    override fun setValue(value: Double) {
        super.setValue(max(minimum, min(maximum, value)))
    }
}