package top.fpsmaster.module.impl.render

import top.fpsmaster.hud.HudEditorScreen
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class HudEditor : Module("hud-editor", Category.UI) {
    private var closingFromScreen = false

    // Editor mode is transient: never persist it as enabled in configs.
    override val persistEnabled: Boolean = false

    override fun onEnable() {
        // Guard against being toggled on before the client is fully constructed
        // (e.g. restored from a config), when there is no screen/mouse handler yet.
        if (mc.mouseHandler == null) {
            return
        }
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
