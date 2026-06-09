package top.fpsmaster.module.impl.ui

import io.github.vlouboos.standaloneevent.api.EventHandler
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.EntityHitResult
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class ComboDisplay : Module("combo-display", Category.UI) {
    init {
        textColor.addTo(this)
        style.addTo(this)
    }

    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        val player = mc.player ?: return
        val value = target

        if (player.hurtTime == 1 || value == null || !value.isAlive || player.distanceTo(value) > 7f) {
            combo = 0
            if (value == null || !value.isAlive || player.distanceTo(value) > 7f) {
                target = null
            }
            return
        }

        if (value.hurtTime == 9 && !countedHit) {
            combo++
            countedHit = true
        } else if (value.hurtTime == 0) {
            countedHit = false
        }
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        target = null
        combo = 0
    }

    companion object {
        private var active = false
        private var target: LivingEntity? = null
        private var countedHit = false
        val textColor = HudTextColor()
        val style = HudStyle()
        var combo = 0
            private set

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun textColorValue(): Int = textColor.argb()

        @JvmStatic
        fun recordAttack(minecraft: Minecraft) {
            if (!active) {
                return
            }

            val entity = (minecraft.hitResult as? EntityHitResult)?.entity as? LivingEntity ?: return
            if (entity !== target) {
                combo = 0
                countedHit = false
            }
            target = entity
        }
    }
}
