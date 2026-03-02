package top.fpsmaster.module.impl.auxiliary

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue

class TimeChanger : Module("timechanger", Category.AUXILIARY) {
    companion object {
        val time = NumberValue("time", 6000.0, 0.0, 24000.0, 1000.0)
        var working = false
    }

    init {
        values.add(time)
    }

    override fun onEnable() {
        working = true
    }

    override fun onDisable() {
        working = false
    }
}