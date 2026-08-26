package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.util.function.Supplier

/**
 * A setting with a fixed set of mutually exclusive options, mapped to Prism `CHOICE`.
 *
 * The stored value is the option id (not its index) so reordering or inserting options in a later
 * release cannot silently repoint a saved profile at a different option. [select] keeps the
 * index-based ClickGUI interaction working, and [index] is what the bridge reports back.
 */
class ChoiceValue(
    identity: String,
    val options: List<String>,
    value: String = options.first(),
    displayable: Supplier<Boolean> = Supplier { true }
) : Value<String>(identity, normalize(options, value), displayable) {

    val index: Int
        get() = options.indexOf(getValue()).coerceAtLeast(0)

    override fun setValue(value: String) {
        options.firstOrNull { it.equals(value, ignoreCase = true) }?.let { super.setValue(it) }
    }

    /** Selects by ClickGUI index; out-of-range indices are ignored. */
    fun select(index: Int) {
        options.getOrNull(index)?.let { super.setValue(it) }
    }

    fun isSelected(option: String): Boolean = getValue().equals(option, ignoreCase = true)

    companion object {
        private fun normalize(options: List<String>, value: String): String {
            require(options.isNotEmpty()) { "ChoiceValue needs at least one option" }
            return options.firstOrNull { it.equals(value, ignoreCase = true) } ?: options.first()
        }
    }
}
