package top.fpsmaster.hud.impl

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.ui.ModsList
import top.fpsmaster.translation.Language

class ModsListHudComponent : HudComponent(
    id = "mods_list",
    x = 10f,
    y = 356f
) {
    override fun shouldRender(): Boolean = visible && ModsList.isActive()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val lines = resolveLines(preview)
        return HudSize(
            width = (lines.maxOfOrNull { mc.font.width(it) } ?: 1).toFloat(),
            height = (lines.size * ModsList.style.lineStep(12)).coerceAtLeast(12).toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val lineStep = ModsList.style.lineStep(12)
        resolveLines(preview).forEachIndexed { index, text ->
            ModsList.style.drawText(guiGraphics, text, 0, index * lineStep, color(index))
        }
    }

    private fun resolveLines(preview: Boolean): List<String> {
        if (preview) {
            return listOf("FPSMaster", "Sprint", "FullBright")
        }

        return ModuleManager.modules.values
            .filter { it.enabled && it.category != Category.UI }
            .map { module -> displayName(module) }
            .sortedByDescending { mc.font.width(it) }
            .let { lines ->
                if (ModsList.shouldShowText()) {
                    listOf(ModsList.titleText()) + lines
                } else {
                    lines
                }
            }
    }

    private fun displayName(module: Module): String {
        val edgeName = edgeModuleNames[module.identity] ?: module.identity
        if (ModsList.shouldUseEnglish()) {
            return edgeName
        }

        val key = edgeName.lowercase()
        val translated = Language.get(key)
        return if (translated == key) edgeName else translated
    }

    private fun color(index: Int): Int {
        if (!ModsList.shouldUseRainbow()) {
            return ModsList.colorValue()
        }

        val hue = ((System.currentTimeMillis() / 50L + index * 8L) % 100L) / 100.0f
        return java.awt.Color.HSBtoRGB(hue, 0.7f, 1.0f) or 0xFF000000.toInt()
    }

    companion object {
        private val edgeModuleNames = mapOf(
            "animation" to "OldAnimations",
            "auto-gg" to "AutoGG",
            "better-chat" to "BetterChat",
            "better-fishing-rod" to "BetterFishingRod",
            "better-screen" to "BetterScreen",
            "block-indicator" to "BlockIndicator",
            "block-overlay" to "BlockOverlay",
            "clean-view" to "CleanView",
            "client-settings" to "ClientSettings",
            "combo-display" to "ComboDisplay",
            "coords-display" to "CoordsDisplay",
            "cps-display" to "CPSDisplay",
            "crosshair" to "Crosshair",
            "custom-fov" to "CustomFOV",
            "custom-titles" to "CustomTitles",
            "damage-indicator" to "DamageIndicator",
            "direction-display" to "DirectionDisplay",
            "fire-modifier" to "FireModifier",
            "fixed-inventory" to "FixedInventory",
            "fps-display" to "FPSDisplay",
            "free-look" to "FreeLook",
            "full-bright" to "FullBright",
            "hide-indicator" to "HideIndicator",
            "hit-color" to "HitColor",
            "hitboxes" to "Hitboxes",
            "inventory-display" to "InventoryDisplay",
            "item-count-display" to "ItemCountDisplay",
            "item-physics" to "ItemPhysics",
            "keystrokes" to "Keystrokes",
            "level-tag" to "NameTags",
            "mini-map" to "MiniMap",
            "minimized-bobbing" to "MinimizedBobbing",
            "mods-list" to "ModsList",
            "more-particles" to "MoreParticles",
            "motion-blur" to "MotionBlur",
            "name-protect" to "NameProtect",
            "no-hit-delay" to "NoHitDelay",
            "no-hurt-cam" to "NoHurtCam",
            "optimization" to "Performance",
            "particles-modifier" to "ParticlesModifier",
            "ping-display" to "PingDisplay",
            "player-display" to "PlayerDisplay",
            "potion-display" to "PotionDisplay",
            "raw-input" to "RawInput",
            "reach-display" to "ReachDisplay",
            "scoreboard" to "Scoreboard",
            "smooth-zoom" to "SmoothZoom",
            "sound-modifier" to "SoundModifier",
            "sprint" to "Sprint",
            "tab-overlay" to "TabOverlay",
            "target-display" to "TargetDisplay",
            "time-changer" to "TimeChanger",
            "tnt-timer" to "TNTTimer"
        )
    }
}
