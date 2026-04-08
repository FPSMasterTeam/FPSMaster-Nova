package top.fpsmaster.module.impl.auxiliary

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

class CustomFOV : Module("customfov", Category.AUXILIARY) {
    init {
        values.addAll(
            arrayOf(
                noSpeedFov,
                noFlyFov,
                noBowFov
            )
        )
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        @JvmField
        val noSpeedFov = OptionValue("no_speed_fov", false)

        @JvmField
        val noFlyFov = OptionValue("no_fly_fov", false)

        @JvmField
        val noBowFov = OptionValue("no_bow_fov", false)

        private var active = false

        @JvmStatic
        fun isNoSpeedFovEnabled(): Boolean = active && noSpeedFov.getValue()

        @JvmStatic
        fun isNoFlyFovEnabled(): Boolean = active && noFlyFov.getValue()

        @JvmStatic
        fun isNoBowFovEnabled(): Boolean = active && noBowFov.getValue()
    }
}
