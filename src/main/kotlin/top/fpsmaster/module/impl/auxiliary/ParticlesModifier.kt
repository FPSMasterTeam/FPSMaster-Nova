package top.fpsmaster.module.impl.auxiliary

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

class ParticlesModifier : Module("particles-modifier", Category.AUXILIARY) {
    init {
        values.add(block)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        @JvmField
        val block = OptionValue("block", true)

        private var active = false

        @JvmStatic
        fun shouldHideBlockParticles(): Boolean = active && !block.getValue()
    }
}
