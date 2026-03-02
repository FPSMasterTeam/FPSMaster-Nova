package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.util.function.Supplier

class OptionValue(identity: String, value: Boolean, displayable: Supplier<Boolean> = { true }) :
    Value<Boolean>(identity, value, displayable)