package top.fpsmaster.hud

import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import top.fpsmaster.hud.impl.SprintTextHudComponent
import top.fpsmaster.mc

object HudManager {
    val components = linkedMapOf<String, HudComponent>()

    fun initialize() {
        register(
            SprintTextHudComponent()
        )
        HudConfigManager.load()
    }

    fun register(vararg components: HudComponent) {
        components.forEach { component ->
            this.components[component.id] = component
        }
    }

    fun render(guiGraphics: GuiGraphics, @Suppress("unused") deltaTracker: DeltaTracker) {
        if (mc.player == null || mc.options.hideGui || mc.screen != null) {
            return
        }

        components.values.forEach { component ->
            component.render(guiGraphics, preview = false)
        }
    }
}
