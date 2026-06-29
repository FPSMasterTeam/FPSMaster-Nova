package top.fpsmaster.module.impl.render

import io.github.vlouboos.standaloneevent.api.EventHandler
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class MoreParticles : Module("more-particles", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                sharpness,
                alwaysSharpness,
                crit,
                alwaysCrit,
                special,
                killEffect
            )
        )
    }

    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        val entity = target ?: return
        if (entity.isAlive) {
            return
        }

        if (lastEffectId != entity.id) {
            spawnKillEffect(entity)
            lastEffectId = entity.id
        }
        target = null
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        target = null
        lastEffectId = null
    }

    companion object {
        @JvmField
        val sharpness = NumberValue("sharpness", 2.0, 0.0, 30.0, 1.0)

        @JvmField
        val alwaysSharpness = OptionValue("always-sharpness", false)

        @JvmField
        val crit = NumberValue("crit", 2.0, 0.0, 30.0, 1.0)

        @JvmField
        val alwaysCrit = OptionValue("always-crit", false)

        @JvmField
        val special = NumberValue("special", 0.0, 0.0, 3.0, 1.0)

        @JvmField
        val killEffect = NumberValue("kill-effect", 0.0, 0.0, 2.0, 1.0)

        private var active = false
        private var target: LivingEntity? = null
        private var lastEffectId: Int? = null

        @JvmStatic
        fun recordAttack(minecraft: Minecraft) {
            if (!active) {
                return
            }

            val hitResult = minecraft.hitResult as? EntityHitResult ?: return
            val entity = hitResult.entity as? LivingEntity ?: return
            target = entity

            val player = minecraft.player ?: return
            repeat(crit.getValue().toInt()) {
                if (player.fallDistance > 0.0f || alwaysCrit.getValue()) {
                    player.crit(entity)
                }
            }

            repeat(sharpness.getValue().toInt()) {
                if (hasSharpness(player.mainHandItem) || alwaysSharpness.getValue()) {
                    player.magicCrit(entity)
                }
            }

            spawnSpecialParticle(entity, hitResult.location, special.getValue().toInt(), minecraft)
        }

        private fun hasSharpness(stack: net.minecraft.world.item.ItemStack): Boolean {
            //? if >=1.21.5 {
            return stack.enchantments.keySet().any { it.`is`(Enchantments.SHARPNESS) }
            //?} else {
            /*return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, stack) > 0*/
            //?}
        }

        private fun spawnSpecialParticle(entity: LivingEntity, hitLocation: Vec3, mode: Int, minecraft: Minecraft) {
            val level = minecraft.level ?: return
            var x = entity.x
            var y = entity.y + entity.bbHeight * 0.5
            var z = entity.z

            when (mode) {
                1 -> level.addParticle(ParticleTypes.HEART, x, y, z, 0.0, 0.15, 0.0)
                2 -> level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.05, 0.0)
                3 -> {
                    x = hitLocation.x
                    y = hitLocation.y
                    z = hitLocation.z
                    //? if >=1.21.5 {
                    level.addParticle(DustParticleOptions(0xCC0000, 1.0f), x, y, z, 0.0, 0.0, 0.0)
                    //?} else {
                    /*level.addParticle(DustParticleOptions(org.joml.Vector3f(0.8f, 0.0f, 0.0f), 1.0f), x, y, z, 0.0, 0.0, 0.0)*/
                    //?}
                    level.playLocalSound(x, y, z, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0f, 1.0f, false)
                }
            }
        }

        private fun spawnKillEffect(entity: LivingEntity) {
            val level = Minecraft.getInstance().level ?: return
            val x = entity.x
            val y = entity.y + entity.bbHeight * 0.5
            val z = entity.z

            when (killEffect.getValue().toInt()) {
                1 -> {
                    repeat(16) {
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.2, 0.0)
                    }
                    level.playLocalSound(x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 1.0f, false)
                }
                2 -> {
                    level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 0.0, 0.0, 0.0)
                    //? if >=1.21.5 {
                    level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0f, 1.0f, false)
                    //?} else {
                    /*level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 1.0f, false)*/
                    //?}
                }
            }
        }
    }
}
