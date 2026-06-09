package top.fpsmaster.module.impl.optimization

import io.github.vlouboos.standaloneevent.api.EventHandler
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class Optimization : Module("optimization", Category.OPTIMIZATION) {
    init {
        values.addAll(
            arrayOf(
                ignoreArmorStand,
                entityCulling,
                fastLoad,
                entityLimitation,
                fpsLosingFocus,
                particleLimitation,
                batchModelRendering,
                lowAnimationTick,
                fontOptimization,
                staticParticleColor,
                chunkLoadingLimitation,
                chunkUpdatingLimitation
            )
        )
    }

    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        particleCount = 0
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        particleCount = 0
        entityRenderCount = 0
    }

    companion object {
        val ignoreArmorStand = OptionValue("ignore-armor-stand", true)
        val entityCulling = OptionValue("entity-culling", false)
        val fastLoad = OptionValue("fast-load", true) // Legacy world-load GC skip has no modern equivalent.
        val entityLimitation = NumberValue("entity-limitation", 200.0, 0.0, 800.0, 1.0, "individual")
        val fpsLosingFocus = NumberValue("fps-losing-focus", 30.0, 0.0, 360.0, 1.0)
        val particleLimitation = NumberValue("particle-limitation", 400.0, 0.0, 2000.0, 1.0)
        val batchModelRendering = OptionValue("batch-model-rendering", true) // Legacy display-list batching has no modern equivalent.
        val lowAnimationTick = OptionValue("low-animation-tick", true)
        val fontOptimization = OptionValue("font-optimization", false) // Legacy FontRenderer display-list cache has no modern equivalent.
        val staticParticleColor = OptionValue("static-particle-color", true)
        val chunkLoadingLimitation = OptionValue("chunk-loading-limitation", true)
        val chunkUpdatingLimitation = NumberValue("chunk-updating-limitation", 50.0, 0.0, 250.0, 1.0, "sections")

        private var active = false
        private var particleCount = 0
        private var entityRenderCount = 0
        private var chunkUpdatesThisFrame = 0

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun shouldIgnoreArmorStand(): Boolean = active && ignoreArmorStand.getValue()

        @JvmStatic
        fun resetEntityRenderCount() {
            entityRenderCount = 0
        }

        @JvmStatic
        fun shouldCullByEntityLimit(): Boolean {
            if (!active || !entityCulling.getValue() || entityLimitation.getValue() <= 0.0) {
                return false
            }

            entityRenderCount += 1
            return entityRenderCount > entityLimitation.getValue().toInt()
        }

        @JvmStatic
        fun shouldCullParticle(): Boolean {
            if (!active || particleLimitation.getValue() <= 0.0) {
                return false
            }

            particleCount += 1
            return particleCount > particleLimitation.getValue().toInt()
        }

        @JvmStatic
        fun shouldUseStaticParticleColor(): Boolean {
            return active && staticParticleColor.getValue()
        }

        @JvmStatic
        fun shouldUseLowAnimationTick(): Boolean {
            return active && lowAnimationTick.getValue()
        }

        @JvmStatic
        fun fpsLimitWhenUnfocused(): Int {
            return if (active) fpsLosingFocus.getValue().toInt() else 0
        }

        @JvmStatic
        fun resetChunkUpdateCount() {
            chunkUpdatesThisFrame = 0
        }

        @JvmStatic
        fun shouldScheduleChunkUpdate(): Boolean {
            if (!active || !chunkLoadingLimitation.getValue() || chunkUpdatingLimitation.getValue() <= 0.0) {
                return true
            }

            chunkUpdatesThisFrame += 1
            return chunkUpdatesThisFrame <= chunkUpdatingLimitation.getValue().toInt()
        }
    }
}
