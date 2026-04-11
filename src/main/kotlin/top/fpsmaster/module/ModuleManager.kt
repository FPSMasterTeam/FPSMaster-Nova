package top.fpsmaster.module

import io.github.vlouboos.standaloneevent.api.EventHandler
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.module.impl.auxiliary.CustomFOV
import top.fpsmaster.module.impl.auxiliary.NameProtect
import top.fpsmaster.module.impl.auxiliary.Sprint
import top.fpsmaster.module.impl.auxiliary.TimeChanger
import top.fpsmaster.module.impl.optimization.BetterFishingRod
import top.fpsmaster.module.impl.optimization.NoHitDelay
import top.fpsmaster.module.impl.optimization.NoHurtCam
import top.fpsmaster.module.impl.render.HudEditor
import top.fpsmaster.module.impl.optimization.Optimization
import top.fpsmaster.module.impl.render.ClickGUI
import top.fpsmaster.module.impl.render.FullBright
import top.fpsmaster.module.impl.render.MinimizedBobbing
import top.fpsmaster.module.impl.render.Animation
import top.fpsmaster.web.network.packets.PacketRegistryInitializer

class ModuleManager {
    companion object {
        val modules = linkedMapOf<String, Module>()

        fun addModule(vararg modules: Module) {
            modules.forEach { ModuleManager.modules[it.identity] = it }
        }

        @JvmStatic
        fun initialize() {
            addModule(
                // Optimization
                BetterFishingRod(),
                NoHitDelay(),
                NoHurtCam(),
                Optimization(),
                // Render
                Animation(),
                ClickGUI(),
                FullBright(),
                HudEditor(),
                MinimizedBobbing(),
                // Auxiliary
                CustomFOV(),
                NameProtect(),
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
                    PacketRegistryInitializer.broadcastModuleSnapshot()
                }
            }
        }
    }
}
