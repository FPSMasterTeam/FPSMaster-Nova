package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class FullBright : Module("full-bright", Category.RENDER) {
    companion object {
        private const val FULL_BRIGHT_GAMMA = 100.0
        private var active = false

        @JvmStatic
        fun adjustGamma(gamma: Double): Double {
            return if (active) FULL_BRIGHT_GAMMA else gamma
        }
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }
}
