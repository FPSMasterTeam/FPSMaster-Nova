package top.fpsmaster.hud

//? if >=1.21 {
import net.minecraft.client.DeltaTracker
//?}
//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
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
import top.fpsmaster.hideGuiCompat
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

    //? if >=1.21 {
    fun render(guiGraphics: GuiGraphics, @Suppress("unused") deltaTracker: DeltaTracker) {
        renderHud(guiGraphics)
    }
    //?} else {
    /*fun render(guiGraphics: GuiGraphics, @Suppress("unused") partialTick: Float) {
        renderHud(guiGraphics)
    }
    *///?}

    // Render the HUD components. Intentionally does NOT bail when a screen is open, so the HUD stays
    // visible over the ClickGUI, inventory, etc. (it is also called from a Screen render hook).
    fun renderHud(guiGraphics: GuiGraphics) {
        if (mc.player == null || hideGuiCompat) {
            return
        }

        val pose = guiGraphics.pose()
        val scale = ClientSettings.hudRenderScale()
        val surfaceWidth = mc.window.guiScaledWidth / scale
        val surfaceHeight = mc.window.guiScaledHeight / scale
        components.values.forEach { it.adaptToSurface(surfaceWidth, surfaceHeight, preview = false) }
        //? if >=1.21.5 {
        pose.pushMatrix()
        pose.scale(scale, scale)
        components.values.forEach { component ->
            component.render(guiGraphics, preview = false)
        }
        pose.popMatrix()
        //?} else {
        /*pose.pushPose()
        pose.scale(scale, scale, 1f)
        components.values.forEach { component ->
            component.render(guiGraphics, preview = false)
        }
        pose.popPose()
        *///?}
    }
}
