package top.fpsmaster.module

import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI

open class Module(val identity: String, val category: Category, var key: Int = 0) {
    var enabled: Boolean = false
        set(value) {
            if (value && !field) {
                field = true
                onEnable()
                StandaloneEventAPI.getApi().register(this)
            } else if (!value && field) {
                StandaloneEventAPI.getApi().unregister(this)
                onDisable()
                field = false
            }
        }

    protected open fun onEnable() {
    }

    protected open fun onDisable() {
    }
}