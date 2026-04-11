package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class MinimizedBobbing : Module("minimizedbobbing", Category.RENDER) {
    companion object {
        var working = false
    }

    override fun onEnable() {
        working = true
    }

    override fun onDisable() {
        working = false
    }
}