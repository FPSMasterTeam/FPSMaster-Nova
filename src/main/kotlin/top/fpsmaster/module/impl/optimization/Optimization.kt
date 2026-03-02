package top.fpsmaster.module.impl.optimization

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
                fontOptimization,
                staticParticleColor,
                chunkLoadingLimitation,
                chunkUpdatingLimitation
            )
        )
    }

    companion object {
        val ignoreArmorStand = OptionValue("ignore_armor_stand", true)
        val entityCulling = OptionValue("entity_culling", true) // TODO
        val fastLoad = OptionValue("fast_load", true) // TODO
        val entityLimitation = NumberValue("entity_limitation", 180.0, 10.0, 500.0, 1.0, "individual") // TODO
        val fpsLosingFocus = NumberValue("fps_losing_focus", 30.0, 5.0, 60.0, 1.0) // TODO
        val particleLimitation = NumberValue("particle_limitation", 100.0, 0.0, 2000.0, 1.0) // TODO
        val fontOptimization = OptionValue("font_optimization", false) // TODO
        val staticParticleColor = OptionValue("static_particle_color", false) // TODO
        val chunkLoadingLimitation = OptionValue("chunk_loading_limitation", true) // TODO
        val chunkUpdatingLimitation = NumberValue("chunk_updating_limitation", 50.0, 1.0, 100.0, 1.0, "ms") // TODO
    }
}