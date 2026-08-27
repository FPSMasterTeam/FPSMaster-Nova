package top.fpsmaster.module

import io.github.vlouboos.standaloneevent.api.EventHandler
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.module.impl.auxiliary.AutoGG
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.module.impl.auxiliary.CustomFOV
import top.fpsmaster.module.impl.auxiliary.LevelTag
import top.fpsmaster.module.impl.auxiliary.NameProtect
import top.fpsmaster.module.impl.auxiliary.ParticlesModifier
import top.fpsmaster.module.impl.auxiliary.SoundModifier
import top.fpsmaster.module.impl.auxiliary.Sprint
import top.fpsmaster.module.impl.auxiliary.TNTTimer
import top.fpsmaster.module.impl.auxiliary.TimeChanger
import top.fpsmaster.module.impl.auxiliary.AutoText
import top.fpsmaster.module.impl.auxiliary.RawInput
import top.fpsmaster.module.impl.auxiliary.Replay
import top.fpsmaster.module.impl.auxiliary.ToggleSneak
import top.fpsmaster.module.impl.optimization.BetterFishingRod
import top.fpsmaster.module.impl.optimization.FixedInventory
import top.fpsmaster.module.impl.optimization.NoHitDelay
import top.fpsmaster.module.impl.optimization.NoHurtCam
import top.fpsmaster.module.impl.optimization.SmoothZoom
import top.fpsmaster.module.impl.render.CleanView
import top.fpsmaster.module.impl.render.Crosshair
import top.fpsmaster.module.impl.render.FireModifier
import top.fpsmaster.module.impl.render.FreeLook
import top.fpsmaster.module.impl.render.HideIndicator
import top.fpsmaster.module.impl.render.HitColor
import top.fpsmaster.module.impl.render.Hitboxes
import top.fpsmaster.module.impl.render.ItemPhysics
import top.fpsmaster.module.impl.optimization.Optimization
import top.fpsmaster.module.impl.render.BlockOverlay
import top.fpsmaster.module.impl.render.CustomFog
import top.fpsmaster.module.impl.render.FullBright
import top.fpsmaster.module.impl.render.MinimizedBobbing
import top.fpsmaster.module.impl.render.MotionBlur
import top.fpsmaster.module.impl.render.Animation
import top.fpsmaster.module.impl.render.DamageIndicator
import top.fpsmaster.module.impl.render.MoreParticles
import top.fpsmaster.module.impl.ui.BetterChat
import top.fpsmaster.module.impl.ui.BetterScreen
import top.fpsmaster.module.impl.ui.ArmorDisplay
import top.fpsmaster.module.impl.ui.BlockIndicator
import top.fpsmaster.module.impl.ui.CoordsDisplay
import top.fpsmaster.module.impl.ui.ComboDisplay
import top.fpsmaster.module.impl.ui.CPSDisplay
import top.fpsmaster.module.impl.ui.CustomTitles
import top.fpsmaster.module.impl.ui.DirectionDisplay
import top.fpsmaster.module.impl.ui.FPSDisplay
import top.fpsmaster.module.impl.ui.InventoryDisplay
import top.fpsmaster.module.impl.ui.ItemCountDisplay
import top.fpsmaster.module.impl.ui.Keystrokes
import top.fpsmaster.module.impl.ui.LyricsDisplay
import top.fpsmaster.module.impl.ui.MiniMap
import top.fpsmaster.module.impl.ui.ModsList
import top.fpsmaster.module.impl.ui.PingDisplay
import top.fpsmaster.module.impl.ui.PlayerDisplay
import top.fpsmaster.module.impl.ui.PotionDisplay
import top.fpsmaster.module.impl.ui.ReachDisplay
import top.fpsmaster.module.impl.ui.Scoreboard
import top.fpsmaster.module.impl.ui.TabOverlay
import top.fpsmaster.module.impl.ui.ChatAvatars
import top.fpsmaster.module.impl.ui.TargetDisplay
import top.fpsmaster.module.impl.ui.ClockDisplay
import top.fpsmaster.module.impl.ui.PerformanceHud
import top.fpsmaster.module.impl.ui.PlayTime
import top.fpsmaster.module.impl.ui.SaturationDisplay
import top.fpsmaster.module.impl.ui.ServerAddressDisplay


class ModuleManager {
    companion object {
        val modules = linkedMapOf<String, Module>()

        fun addModule(vararg modules: Module) {
            modules.forEach { ModuleManager.modules[it.identity] = it }
        }

        @JvmStatic
        fun initialize() {
            addModule(
                // Optimization
                BetterFishingRod(),
                FixedInventory(),
                NoHitDelay(),
                NoHurtCam(),
                Optimization(),
                SmoothZoom(),
                // Render
                Animation(),
                BlockOverlay(),
                CleanView(),
                CustomFog(),
                Crosshair(),
                DamageIndicator(),
                FireModifier(),
                FreeLook(),
                FullBright(),
                HideIndicator(),
                HitColor(),
                Hitboxes(),
                ItemPhysics(),
                MinimizedBobbing(),
                MotionBlur(),
                MoreParticles(),
                // Auxiliary
                AutoGG(),
                ClientSettings(),
                CustomFOV(),
                LevelTag(),
                NameProtect(),
                ParticlesModifier(),
                SoundModifier(),
                Sprint(),
                TNTTimer(),
                TimeChanger(),
                AutoText(),
                RawInput(),
                Replay(),
                ToggleSneak(),
                // UI
                ArmorDisplay(),
                BetterChat(),
                BetterScreen(),
                BlockIndicator(),
                CoordsDisplay(),
                ComboDisplay(),
                CPSDisplay(),
                CustomTitles(),
                DirectionDisplay(),
                FPSDisplay(),
                InventoryDisplay(),
                ItemCountDisplay(),
                Keystrokes(),
                LyricsDisplay(),
                MiniMap(),
                ModsList(),
                PingDisplay(),
                PlayerDisplay(),
                PotionDisplay(),
                ReachDisplay(),
                Scoreboard(),
                TabOverlay(),
                ChatAvatars(),
                ClockDisplay(),
                PerformanceHud(),
                PlayTime(),
                SaturationDisplay(),
                ServerAddressDisplay(),
                TargetDisplay()
            )
            StandaloneEventAPI.getApi().register(ModuleManager::class.java)
        }

        @JvmStatic
        @Suppress("unused")
        @EventHandler
        fun onKey(e: KeyEvent) {
            modules.values.forEach {
                if (it.key == e.key.value) {
                    it.enabled = !it.enabled
                    ConfigManager.saveDefault()
                }
            }
        }
    }
}
