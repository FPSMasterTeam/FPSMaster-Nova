package top.fpsmaster.module.impl.render

import io.github.vlouboos.standaloneevent.api.EventHandler
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.EntityHitResult
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import java.util.Locale
import kotlin.math.max

class DamageIndicator : Module("damage-indicator", Category.RENDER) {
    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        val value = target ?: return

        val currentHealth = max(0f, value.health)
        val damage = previousHealth - currentHealth
        if (damage != 0f) {
            indicators.add(DamageEntry(value.id, damage, 60))
            previousHealth = currentHealth
        }

        indicators.replaceAll { it.copy(ticks = it.ticks - 1) }
        indicators.removeIf { it.ticks <= 0 }
        if (!value.isAlive) {
            target = null
            previousHealth = 0f
        }
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        target = null
        previousHealth = 0f
        indicators.clear()
    }

    companion object {
        private var active = false
        private var target: LivingEntity? = null
        private var previousHealth = 0f
        private val indicators = mutableListOf<DamageEntry>()

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun recordAttack(minecraft: Minecraft) {
            if (!active) {
                return
            }

            val entity = (minecraft.hitResult as? EntityHitResult)?.entity as? LivingEntity ?: return
            target = entity
            previousHealth = entity.health
        }

        fun displayEntries(): List<DisplayEntry> {
            return indicators.map { entry ->
                entry.toDisplayEntry()
            }
        }

        @JvmStatic
        fun displayEntriesFor(entityId: Int): List<DisplayEntry> {
            return indicators
                .filter { it.entityId == entityId }
                .map { it.toDisplayEntry() }
        }
    }

    data class DisplayEntry(val text: String, val color: Int, val yOffset: Double)

    private data class DamageEntry(val entityId: Int, val damage: Float, val ticks: Int) {
        fun toDisplayEntry(): DisplayEntry {
            val text = String.format(Locale.US, "%.2f", -damage)
            val color = if (damage > 0f) 0xFFFF3333.toInt() else 0xFF32FF32.toInt()
            val yOffset = (60 - ticks).coerceAtLeast(0) / 60.0 * 0.55
            return DisplayEntry(text, color, yOffset)
        }
    }
}
