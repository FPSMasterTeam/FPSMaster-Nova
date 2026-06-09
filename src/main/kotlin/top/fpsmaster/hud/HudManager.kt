package top.fpsmaster.hud

import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import top.fpsmaster.hud.impl.ArmorHudComponent
import top.fpsmaster.hud.impl.BlockIndicatorHudComponent
import top.fpsmaster.hud.impl.ComboTextHudComponent
import top.fpsmaster.hud.impl.CoordsTextHudComponent
import top.fpsmaster.hud.impl.CpsTextHudComponent
import top.fpsmaster.hud.impl.DamageIndicatorHudComponent
import top.fpsmaster.hud.impl.DirectionTextHudComponent
import top.fpsmaster.hud.impl.FpsTextHudComponent
import top.fpsmaster.hud.impl.InventoryHudComponent
import top.fpsmaster.hud.impl.ItemCountHudComponent
import top.fpsmaster.hud.impl.KeystrokesHudComponent
import top.fpsmaster.hud.impl.MiniMapHudComponent
import top.fpsmaster.hud.impl.ModsListHudComponent
import top.fpsmaster.hud.impl.PingTextHudComponent
import top.fpsmaster.hud.impl.PlayerDisplayHudComponent
import top.fpsmaster.hud.impl.PotionTextHudComponent
import top.fpsmaster.hud.impl.ReachTextHudComponent
import top.fpsmaster.hud.impl.ScoreboardHudComponent
import top.fpsmaster.hud.impl.SprintTextHudComponent
import top.fpsmaster.hud.impl.TargetHudComponent
import top.fpsmaster.hud.impl.TNTTimerHudComponent
import top.fpsmaster.mc
import top.fpsmaster.module.impl.auxiliary.ClientSettings

object HudManager {
    val components = linkedMapOf<String, HudComponent>()

    fun initialize() {
        StandaloneEventAPI.getApi().register(CpsTracker)
        register(
            SprintTextHudComponent(),
            CpsTextHudComponent(),
            FpsTextHudComponent(),
            KeystrokesHudComponent(),
            CoordsTextHudComponent(),
            PingTextHudComponent(),
            ArmorHudComponent(),
            DirectionTextHudComponent(),
            InventoryHudComponent(),
            ItemCountHudComponent(),
            MiniMapHudComponent(),
            PotionTextHudComponent(),
            ReachTextHudComponent(),
            ModsListHudComponent(),
            PlayerDisplayHudComponent(),
            TargetHudComponent(),
            ComboTextHudComponent(),
            ScoreboardHudComponent(),
            BlockIndicatorHudComponent(),
            TNTTimerHudComponent(),
            DamageIndicatorHudComponent()
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

        val pose = guiGraphics.pose()
        val scale = ClientSettings.hudRenderScale()
        pose.pushMatrix()
        pose.scale(scale, scale)
        components.values.forEach { component ->
            component.render(guiGraphics, preview = false)
        }
        pose.popMatrix()
    }
}
