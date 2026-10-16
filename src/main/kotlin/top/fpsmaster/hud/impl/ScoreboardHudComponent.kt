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
import net.minecraft.network.chat.Component
//? if >=1.20.5 {
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
//?}
import net.minecraft.world.scores.PlayerTeam
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
        guiGraphics.drawString(mc.font, Scoreboard.style.component(content.title), PADDING_X, PADDING_Y, 0xFFFFFFFF.toInt(), Scoreboard.style.fontShadow.getValue())

        content.lines.forEachIndexed { index, line ->
            guiGraphics.drawString(
                mc.font,
                Scoreboard.style.component(line),
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
        // Same selection and order as vanilla's Gui.displayScoreboardSidebar: the team-colour sidebar
        // slot wins over the plain one, the 15 highest scores are shown top-down (ties by name), and
        // each owner is wrapped in its team prefix/suffix — Hypixel-style boards put all of the
        // visible text there and use the owner only as an invisible unique key.
        val playerTeam = mc.player?.let { scoreboard.getPlayersTeam(it.scoreboardName) }
        //? if >=1.20.5 {
        //? if >=26 {
        /*val teamSlot = playerTeam?.color?.orElse(null)?.displaySlot()
        *///?} else {
        val teamSlot = playerTeam?.let { DisplaySlot.teamColorToSlot(it.color) }
        //?}
        val teamObjective = teamSlot?.let { scoreboard.getDisplayObjective(it) }
        val objective = teamObjective ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return null
        val numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)
        val lines = scoreboard.listPlayerScores(objective)
            .filter { !it.isHidden }
            .sortedWith(SCORE_DISPLAY_ORDER)
            .take(MAX_LINES)
            .map { entry ->
                val name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName())
                val value = entry.formatValue(numberFormat)
                if (Scoreboard.shouldShowScore() && value.string.isNotEmpty()) {
                    name.copy().append(Component.literal(": ")).append(value)
                } else {
                    name
                }
            }
        //?} else {
        /*val teamColor = playerTeam?.color?.id ?: -1
        val teamObjective = if (teamColor >= 0) scoreboard.getDisplayObjective(3 + teamColor) else null
        val objective = teamObjective ?: scoreboard.getDisplayObjective(1) ?: return null
        // getPlayerScores is already sorted ascending (Score.SCORE_COMPARATOR, ties by name), and
        // vanilla draws the last 15 bottom-up: highest score on top.
        val lines = scoreboard.getPlayerScores(objective)
            .filter { it.owner != null && !it.owner.startsWith("#") }
            .takeLast(MAX_LINES)
            .asReversed()
            .map { entry ->
                val name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner), Component.literal(entry.owner))
                if (Scoreboard.shouldShowScore()) {
                    name.append(Component.literal(": ${entry.score}"))
                } else {
                    name
                }
            }
        *///?}

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
        //? if >=1.20.5 {
        private val SCORE_DISPLAY_ORDER: Comparator<PlayerScoreEntry> =
            compareByDescending<PlayerScoreEntry> { it.value() }
                .thenComparing({ it.owner() }, String.CASE_INSENSITIVE_ORDER)
        //?}
        private const val MAX_LINES = 15
        private const val LINE_HEIGHT = 10
        private const val PADDING_X = 3
        private const val PADDING_Y = 2
    }
}
