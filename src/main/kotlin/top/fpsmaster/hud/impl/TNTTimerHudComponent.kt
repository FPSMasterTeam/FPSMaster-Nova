package top.fpsmaster.hud.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.item.PrimedTnt
import java.util.Locale
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.auxiliary.TNTTimer

class TNTTimerHudComponent : HudComponent(
    id = "tnt_timer",
    x = 10f,
    y = 542f
) {
    override fun shouldRender(): Boolean = visible && TNTTimer.isActive() && resolveEntries().isNotEmpty()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val entries = if (preview) previewEntries else resolveEntries()
        val width = entries.maxOfOrNull { mc.font.width(it.text) } ?: 1
        return HudSize(width = width.toFloat(), height = (entries.size * 12).coerceAtLeast(12).toFloat())
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val entries = if (preview) previewEntries else resolveEntries()
        entries.forEachIndexed { index, entry ->
            guiGraphics.drawString(mc.font, entry.text, 0, index * 12, entry.color, true)
        }
    }

    private fun resolveEntries(): List<TimerEntry> {
        val player = mc.player ?: return emptyList()
        return mc.level?.entitiesForRendering()
            ?.asSequence()
            ?.filterIsInstance<PrimedTnt>()
            ?.sortedBy { it.distanceToSqr(player) }
            ?.take(5)
            ?.mapIndexed { index, entity ->
                val seconds = entity.fuse / 20.0 + TNTTimer.duration.getValue() - 4.0
                TimerEntry("TNT ${index + 1}: ${String.format(Locale.US, "%.2f", seconds)}s", colorFor(seconds))
            }
            ?.toList()
            ?: emptyList()
    }

    private fun colorFor(seconds: Double): Int {
        return when {
            seconds < 1.0 -> 0xFFFF3333.toInt()
            seconds < 2.5 -> 0xFFFFFF33.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private data class TimerEntry(val text: String, val color: Int)

    companion object {
        private val previewEntries = listOf(
            TimerEntry("TNT 1: 2.40s", 0xFFFFFF33.toInt()),
            TimerEntry("TNT 2: 0.80s", 0xFFFF3333.toInt())
        )
    }
}
