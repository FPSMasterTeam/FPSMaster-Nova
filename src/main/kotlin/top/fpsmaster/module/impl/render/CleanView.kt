package top.fpsmaster.module.impl.render

import net.minecraft.world.entity.LivingEntity
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class CleanView : Module("clean-view", Category.RENDER) {
    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false

        @JvmStatic
        fun shouldHideEffectParticles(entity: LivingEntity): Boolean {
            return active && entity === mc.player
        }
    }
}
