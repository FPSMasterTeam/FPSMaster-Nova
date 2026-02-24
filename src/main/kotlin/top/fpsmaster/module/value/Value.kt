package top.fpsmaster.module.value

import java.util.function.Supplier

open class Value<V>(private val identity: String, private var value: V, private val displayable: Supplier<Boolean>) {
    fun getIdentity(): String = identity
    
    fun getValue(): V = value

    open fun setValue(value: V) {
        this.value = value
    }

    fun isDisplayable(): Boolean {
        return displayable.get()
    }
}