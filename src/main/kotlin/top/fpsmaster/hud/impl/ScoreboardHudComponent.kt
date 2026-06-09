package top.fpsmaster.hud.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.Scoreboard

class ScoreboardHudComponent : HudComponent(
    id = "scoreboard",
    x = 10f,
    y = 200f
) {
    override fun shouldRender(): Boolean = visible && Scoreboard.isActive() && resolveContent(preview = false) != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val content = resolveContent(preview) ?: return HudSize(width = 1f, height = 1f)
        return HudSize(
            width = (content.maxWidth + PADDING_X * 2).toFloat(),
            height = ((content.lines.size + 1) * LINE_HEIGHT + PADDING_Y * 2).toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val content = resolveContent(preview) ?: return
        val width = content.maxWidth + PADDING_X * 2
        val height = (content.lines.size + 1) * LINE_HEIGHT + PADDING_Y * 2

        Scoreboard.style.fillBackground(guiGraphics, 0, 0, width, height)
        guiGraphics.drawString(mc.font, content.title, PADDING_X, PADDING_Y, 0xFFFFFFFF.toInt(), Scoreboard.style.fontShadow.getValue())

        content.lines.forEachIndexed { index, line ->
            guiGraphics.drawString(
                mc.font,
                line,
                PADDING_X,
                PADDING_Y + (index + 1) * LINE_HEIGHT,
                0xFFFFFFFF.toInt(),
                Scoreboard.style.fontShadow.getValue()
            )
        }
    }

    private fun resolveContent(preview: Boolean): Content? {
        if (preview) {
            val lines = listOf(
                Component.literal("PlayerOne"),
                Component.literal("PlayerTwo")
            )
            return Content(Component.literal("Scoreboard"), lines, lines.maxWidth(Component.literal("Scoreboard")))
        }

        val level = mc.level ?: return null
        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return null
        val lines = scoreboard.listPlayerScores(objective)
            .filter { !it.isHidden && !it.owner().startsWith("#") }
            .takeLast(MAX_LINES)
            .map { entry ->
                if (Scoreboard.shouldShowScore()) {
                    entry.ownerName().copy().append(Component.literal(": ${entry.value()}"))
                } else {
                    entry.ownerName()
                }
            }

        return Content(objective.displayName, lines, lines.maxWidth(objective.displayName))
    }

    private fun List<Component>.maxWidth(title: Component): Int {
        return maxOf(
            mc.font.width(title),
            maxOfOrNull { mc.font.width(it) } ?: 1
        )
    }

    private data class Content(
        val title: Component,
        val lines: List<Component>,
        val maxWidth: Int
    )

    companion object {
        private const val MAX_LINES = 15
        private const val LINE_HEIGHT = 10
        private const val PADDING_X = 3
        private const val PADDING_Y = 2
    }
}
