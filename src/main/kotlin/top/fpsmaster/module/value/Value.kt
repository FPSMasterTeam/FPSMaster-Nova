package top.fpsmaster.module.value

import java.util.function.Supplier

/**
 * Optional grouping metadata for a setting. Settings sharing the same [id] are rendered together
 * under one collapsible section in the ClickGUI. [id] is a stable key; the frontend/back-end resolve
 * a localized label from it (translation key `group.<id>`). [collapsedByDefault] hints the initial
 * folded state. Reusable shared groups (e.g. "background", "font", "style") are stamped by the
 * composite templates in [top.fpsmaster.module.impl.ui.HudStyle] / HudTextColor so modules get grouped,
 * collapsible settings for free.
 */
data class ValueGroup(val id: String, val collapsedByDefault: Boolean = false)

open class Value<V>(private val identity: String, private var value: V, private val displayable: Supplier<Boolean>) {
    /** Optional collapsible-group this setting belongs to (null = ungrouped / default section). */
    var group: ValueGroup? = null
        private set

    fun getIdentity(): String = identity

    fun getValue(): V = value

    open fun setValue(value: V) {
        this.value = value
    }

    fun isDisplayable(): Boolean {
        return displayable.get()
    }

    /** Fluent: assign this setting to a collapsible group. Returns this for chaining. */
    fun inGroup(id: String, collapsedByDefault: Boolean = false): Value<V> {
        this.group = ValueGroup(id, collapsedByDefault)
        return this
    }

    /** Fluent: assign this setting to an existing group instance. */
    fun inGroup(group: ValueGroup?): Value<V> {
        this.group = group
        return this
    }
}
