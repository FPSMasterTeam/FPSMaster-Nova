package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

class Animation : Module("animation", Category.RENDER) {
    companion object {
        val noWhiteHeart = OptionValue("no-white-heart", false)
        val oldArmor = OptionValue("old-armor", false)
        val oldBackward = OptionValue("old-backward", false)
        val oldBlocking = OptionValue("old-blocking", false)
        val oldSwing = OptionValue("old-swing", false)
        val oldRod = OptionValue("old-rod", false)
    }

    init {
        values.addAll(
            arrayOf(
                noWhiteHeart,
                oldArmor,
                oldBackward,
                oldBlocking,
                oldSwing,
                oldRod
            )
        )
        canBeEnabled = false
    }
}