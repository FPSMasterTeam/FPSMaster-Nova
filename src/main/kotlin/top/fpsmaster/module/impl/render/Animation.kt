package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class Animation : Module("animation", Category.RENDER) {
    companion object {
        val noWhiteHeart = OptionValue("no-white-heart", false)
        val oldArmor = OptionValue("old-armor", false)
        val oldBackward = OptionValue("old-backward", false)
        val oldBlocking = OptionValue("old-blocking", true)
        val animationMode = NumberValue("animation-mode", 0.0, 0.0, 10.0, 1.0) { oldBlocking.getValue() }
        val oldSwing = OptionValue("old-swing", true)
        val oldRod = OptionValue("old-rod", true)
        val noShield = OptionValue("no-shield", true)
        val animationSneak = OptionValue("animation-sneak", true)
        val oldBow = OptionValue("old-bow", true)
        val oldUsing = OptionValue("old-using", true)
        val blockSwing = OptionValue("block-swing", true)
        val oldDamage = OptionValue("old-damage", true)
        val oldThirdPerson = OptionValue("old-third-person", true)
        val x = NumberValue("x", 0.0, -1.0, 1.0, 0.01)
        val y = NumberValue("y", 0.0, -1.0, 1.0, 0.01)
        val z = NumberValue("z", 0.0, -1.0, 1.0, 0.01)
        val scale = NumberValue("scale", 1.0, 0.0, 3.0, 0.01)

        private var active = false

        @JvmStatic
        fun isActive(): Boolean = active
    }

    init {
        values.addAll(
            arrayOf(
                noWhiteHeart,
                oldArmor,
                oldBackward,
                oldBlocking,
                animationMode,
                oldSwing,
                oldRod,
                noShield,
                animationSneak,
                oldBow,
                oldUsing,
                blockSwing,
                oldDamage,
                oldThirdPerson,
                x,
                y,
                z,
                scale
            )
        )
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }
}
