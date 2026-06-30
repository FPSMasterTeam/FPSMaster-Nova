package top.fpsmaster.hud.impl

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.world.entity.player.Player
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.PlayerDisplay

class PlayerDisplayHudComponent : HudComponent(
    id = "player_display",
    x = 10f,
    y = 396f
) {
    override fun shouldRender(): Boolean = visible && PlayerDisplay.isActive() && resolveLines(false).isNotEmpty()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val lines = resolveLines(preview)
        return HudSize(
            width = ((lines.maxOfOrNull { mc.font.width(it.name) + 8 + mc.font.width(it.healthText) } ?: 36) + 4).toFloat(),
            height = (lines.size * ROW_STEP).coerceAtLeast(ROW_HEIGHT).toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        resolveLines(preview).forEachIndexed { index, line ->
            val y = index * ROW_STEP
            val nameWidth = mc.font.width(line.name)
            val width = 10 + nameWidth + mc.font.width(line.healthText)
            PlayerDisplay.style.fillBackground(guiGraphics, 0, y, width, y + 12)
            guiGraphics.fill(0, y, (width * line.healthRatio).toInt().coerceIn(0, width), y + 12, 0x33000000)
            guiGraphics.drawString(mc.font, line.name, 2, y + 2, 0xFFFFFFFF.toInt(), PlayerDisplay.style.fontShadow.getValue())
            guiGraphics.drawString(mc.font, line.healthText, 8 + nameWidth, y + 2, line.healthColor, PlayerDisplay.style.fontShadow.getValue())
        }
    }

    private fun resolveLines(preview: Boolean): List<Line> {
        if (preview) {
            return listOf(
                Line("Player", "20 hp", 1.0f, 0xFF32FF37.toInt()),
                Line("Target", "16 hp", 0.8f, 0xFF32FF37.toInt())
            )
        }

        val player = mc.player ?: return emptyList()
        return mc.level?.players()
            ?.asSequence()
            ?.filter { it !== player && !it.isInvisible }
            ?.take(10)
            ?.map { line(it) }
            ?.toList()
            ?: emptyList()
    }

    private fun line(player: Player): Line {
        val maxHealth = player.maxHealth.coerceAtLeast(1.0f)
        val health = player.health.coerceAtLeast(0.0f)
        return Line(
            name = player.displayName?.string ?: "",
            healthText = "${health.toInt()} hp",
            healthRatio = (health / maxHealth).coerceIn(0.0f, 1.0f),
            healthColor = healthColor(health, maxHealth)
        )
    }

    private fun healthColor(health: Float, maxHealth: Float): Int {
        return when {
            health >= maxHealth * 0.8f -> 0xFF32FF37.toInt()
            health > maxHealth * 0.5f -> 0xFFFFFF37.toInt()
            else -> 0xFFFF3737.toInt()
        }
    }

    private data class Line(
        val name: String,
        val healthText: String,
        val healthRatio: Float,
        val healthColor: Int
    )

    companion object {
        private const val ROW_HEIGHT = 14
        private const val ROW_STEP = 16
    }
}
