package top.fpsmaster.hud.impl

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.render.DamageIndicator

class DamageIndicatorHudComponent : HudComponent(
    id = "damage_indicator",
    x = 10f,
    y = 572f
) {
    override fun shouldRender(): Boolean = visible && DamageIndicator.isActive() && DamageIndicator.displayEntries().isNotEmpty()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val entries = if (preview) previewEntries else DamageIndicator.displayEntries()
        val width = entries.maxOfOrNull { mc.font.width(it.text) } ?: 1
        return HudSize(width = width.toFloat(), height = (entries.size * 12).coerceAtLeast(12).toFloat())
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val entries = if (preview) previewEntries else DamageIndicator.displayEntries()
        entries.forEachIndexed { index, entry ->
            guiGraphics.drawString(mc.font, entry.text, 0, index * 12, entry.color, true)
        }
    }

    companion object {
        private val previewEntries = listOf(
            DamageIndicator.DisplayEntry("-3.50", 0xFFFF3333.toInt(), 0.0),
            DamageIndicator.DisplayEntry("-1.00", 0xFFFF3333.toInt(), 0.0)
        )
    }
}
