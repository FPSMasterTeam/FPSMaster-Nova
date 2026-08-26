package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.util.function.Supplier

/**
 * A single key binding, mapped to Prism `KEY`. The stored value is a GLFW key code; 0 means unbound.
 *
 * [nameOf] resolves display names without touching Minecraft's `InputConstants`, whose accessors moved
 * between the Stonecutter targets; the GLFW codes themselves are identical on all of them.
 */
class KeyValue(
    identity: String,
    value: Int = 0,
    displayable: Supplier<Boolean> = Supplier { true }
) : Value<Int>(identity, value, displayable) {

    fun keyName(): String = nameOf(getValue())

    fun isBound(): Boolean = getValue() != 0

    companion object {
        private val NAMES: Map<Int, String> = buildMap {
            put(0, "None")
            put(32, "Space")
            put(39, "'")
            put(44, ",")
            put(45, "-")
            put(46, ".")
            put(47, "/")
            (48..57).forEach { put(it, ('0' + (it - 48)).toString()) }
            put(59, ";")
            put(61, "=")
            (65..90).forEach { put(it, ('A' + (it - 65)).toString()) }
            put(91, "[")
            put(92, "\\")
            put(93, "]")
            put(96, "`")
            put(256, "Esc")
            put(257, "Enter")
            put(258, "Tab")
            put(259, "Backspace")
            put(260, "Insert")
            put(261, "Delete")
            put(262, "Right")
            put(263, "Left")
            put(264, "Down")
            put(265, "Up")
            put(266, "Page Up")
            put(267, "Page Down")
            put(268, "Home")
            put(269, "End")
            put(280, "Caps Lock")
            put(284, "Pause")
            (290..301).forEach { put(it, "F${it - 289}") }
            (320..329).forEach { put(it, "Num ${it - 320}") }
            put(330, "Num .")
            put(331, "Num /")
            put(332, "Num *")
            put(333, "Num -")
            put(334, "Num +")
            put(335, "Num Enter")
            put(340, "Left Shift")
            put(341, "Left Ctrl")
            put(342, "Left Alt")
            put(343, "Left Super")
            put(344, "Right Shift")
            put(345, "Right Ctrl")
            put(346, "Right Alt")
            put(347, "Right Super")
            put(348, "Menu")
        }

        fun nameOf(keyCode: Int): String = NAMES[keyCode] ?: "Key $keyCode"
    }
}
