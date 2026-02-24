package top.fpsmaster.module

import io.github.vlouboos.standaloneevent.api.EventHandler
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.module.impl.auxiliary.Sprint
import top.fpsmaster.module.impl.auxiliary.TimeChanger

class ModuleManager {
    companion object {
        val modules = linkedMapOf<String, Module>()

        fun addModule(vararg modules: Module) {
            modules.forEach { ModuleManager.modules[it.identity] = it }
        }

        @JvmStatic
        fun initialize() {
            addModule(
                Sprint(),
                TimeChanger()
            )
            StandaloneEventAPI.getApi().register(ModuleManager::class.java)
        }

        @JvmStatic
        @Suppress("unused")
        @EventHandler
        fun onKey(e: KeyEvent) {
            modules.values.forEach {
                if (it.key == e.key.value) {
                    it.enabled = !it.enabled
                }
            }
        }
    }
}