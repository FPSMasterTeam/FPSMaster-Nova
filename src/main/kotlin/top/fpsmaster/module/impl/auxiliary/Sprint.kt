package top.fpsmaster.module.impl.auxiliary

import io.github.vlouboos.standaloneevent.api.EventHandler
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class Sprint : Module("sprint", Category.AUXILIARY) {
    @EventHandler
    fun onTick(@Suppress("unused") e: TickEvent) {
        mc.options.keySprint.isDown = true
    }
}