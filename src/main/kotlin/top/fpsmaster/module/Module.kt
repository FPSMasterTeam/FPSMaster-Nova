package top.fpsmaster.module

import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import top.fpsmaster.mc
import top.fpsmaster.module.value.Value
import top.fpsmaster.notification.NotificationManager
import top.fpsmaster.translation.Language
import java.util.Locale

open class Module(val identity: String, val category: Category, var key: Int = 0, var canBeEnabled: Boolean = true) {
    val values = mutableListOf<Value<*>>()
    var enabled: Boolean = false
        set(value) {
            if (!canBeEnabled) return
            if (value && !field) {
                field = true
                onEnable()
                StandaloneEventAPI.getApi().register(this)
                notifyToggle(true)
            } else if (!value && field) {
                StandaloneEventAPI.getApi().unregister(this)
                onDisable()
                field = false
                notifyToggle(false)
            }
        }

    protected open fun onEnable() {
    }

    protected open fun onDisable() {
    }

    private fun notifyToggle(enabled: Boolean) {
        if (mc.player == null) {
            return
        }

        val moduleName = Language.get(identity.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() })
        val titleKey = if (enabled) "notification.module.enable" else "notification.module.disable"
        val descriptionKey = if (enabled) "notification.module.enable.desc" else "notification.module.disable.desc"
        NotificationManager.add(
            Language.get(titleKey),
            Language.get(descriptionKey).format(moduleName),
            2f
        )
    }
}
