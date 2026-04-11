package top.fpsmaster.module.impl.render

import top.fpsmaster.hud.HudEditorScreen
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class HudEditor : Module("hud-editor", Category.UI) {
    private var closingFromScreen = false

    override fun onEnable() {
        if (mc.screen !is HudEditorScreen) {
            mc.setScreen(HudEditorScreen(this))
        }
    }

    override fun onDisable() {
        if (!closingFromScreen && mc.screen is HudEditorScreen) {
            mc.setScreen(null)
        }
        closingFromScreen = false
    }

    fun onEditorClosed() {
        if (enabled) {
            closingFromScreen = true
            enabled = false
        }
    }
}
