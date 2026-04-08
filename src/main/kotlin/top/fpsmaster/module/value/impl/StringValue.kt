package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.util.function.Predicate
import java.util.function.Supplier

class StringValue(
    identity: String,
    value: String,
    displayable: Supplier<Boolean> = Supplier { true },
    private val validator: Predicate<String> = Predicate { true }
) : Value<String>(identity, value, displayable) {
    override fun setValue(value: String) {
        if (!validator.test(value)) {
            throw IllegalArgumentException("Invalid string value")
        }
        super.setValue(value)
    }
}
