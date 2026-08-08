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
import net.minecraft.world.entity.player.Player
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.TargetDisplay

class TargetHudComponent : HudComponent(
    id = "target_hud",
    x = 10f,
    y = 432f
) {
    override fun shouldRender(): Boolean = visible &&
        TargetDisplay.isActive() &&
        TargetDisplay.hudMode() != 2 &&
        TargetDisplay.activeHudTarget() != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        return if (TargetDisplay.hudMode() == 1) {
            HudSize(width = 132f, height = 40f)
        } else {
            HudSize(width = 112f, height = 28f)
        }
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val target = if (preview) null else TargetDisplay.activeHudTarget()
        val name = displayName(target)
        val health = target?.health ?: 16f
        val maxHealth = target?.maxHealth ?: 20f

        if (TargetDisplay.hudMode() == 1) {
            renderFancy(guiGraphics, name, health, maxHealth)
        } else {
            renderSimple(guiGraphics, name, health, maxHealth)
        }
    }

    private fun renderSimple(guiGraphics: GuiGraphics, name: String, health: Float, maxHealth: Float) {
        val healthWidth = ((health / maxHealth).coerceIn(0f, 1f) * 108).toInt()
        guiGraphics.fill(0, 0, 112, 28, 0xAA000000.toInt())
        guiGraphics.fill(2, 22, 2 + healthWidth, 26, healthColor(health, maxHealth))
        guiGraphics.drawString(mc.font, name, 4, 4, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(mc.font, "${health.toInt()} hp", 4, 14, 0xFFFFFFFF.toInt(), false)
    }

    private fun renderFancy(guiGraphics: GuiGraphics, name: String, health: Float, maxHealth: Float) {
        val healthWidth = ((health / maxHealth).coerceIn(0f, 1f) * 112).toInt()
        guiGraphics.fill(0, 0, 132, 40, 0xAA000000.toInt())
        guiGraphics.fill(10, 30, 10 + healthWidth, 34, healthColor(health, maxHealth))
        guiGraphics.drawString(mc.font, name, 10, 8, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(mc.font, "${health.toInt()} / ${maxHealth.toInt()} hp", 10, 18, 0xFFFFFFFF.toInt(), false)
    }

    private fun displayName(target: Player?): String {
        val name = target?.displayName?.string ?: "Target"
        return if (TargetDisplay.shouldOmitName() && name.length > 20) {
            name.take(20) + ".."
        } else {
            name
        }
    }

    private fun healthColor(health: Float, maxHealth: Float): Int {
        return when {
            health >= maxHealth * 0.8f -> 0xFF32FF37.toInt()
            health > maxHealth * 0.5f -> 0xFFFFFF37.toInt()
            else -> 0xFFFF3737.toInt()
        }
    }
}
