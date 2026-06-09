package top.fpsmaster.hud.impl

import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.effect.MobEffectInstance
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.PotionDisplay

class PotionTextHudComponent : HudComponent(
    id = "potion_text",
    x = 10f,
    y = 300f
) {
    override fun shouldRender(): Boolean = visible && PotionDisplay.isActive() && effects().isNotEmpty()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val rows = rows(preview)
        val width = rows.maxOfOrNull { row ->
            maxOf(mc.font.width(row.title), mc.font.width(row.duration)) + TEXT_X + PADDING_RIGHT
        } ?: 1
        val height = rows.size * ROW_HEIGHT + (rows.size - 1).coerceAtLeast(0) * PotionDisplay.style.spacing.getValue().toInt()
        return HudSize(width = width.toFloat().coerceAtLeast(1f), height = height.toFloat().coerceAtLeast(ROW_HEIGHT.toFloat()))
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val spacing = PotionDisplay.style.spacing.getValue().toInt()
        rows(preview).forEachIndexed { index, row ->
            val y = index * (ROW_HEIGHT + spacing)
            val width = maxOf(mc.font.width(row.title), mc.font.width(row.duration)) + TEXT_X + PADDING_RIGHT
            PotionDisplay.style.fillBackground(guiGraphics, 0, y, width, y + ROW_HEIGHT)
            row.effect?.let { effect ->
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect.effect), ICON_X, y + ICON_Y, ICON_SIZE, ICON_SIZE)
            } ?: guiGraphics.fill(ICON_X, y + ICON_Y, ICON_X + ICON_SIZE, y + ICON_Y + ICON_SIZE, 0x55FFFFFF)

            guiGraphics.drawString(mc.font, row.title, TEXT_X, y + 5, 0xFFFFFFFF.toInt(), PotionDisplay.style.fontShadow.getValue())
            guiGraphics.drawString(mc.font, row.duration, TEXT_X, y + 18, 0xFFCCCCCC.toInt(), PotionDisplay.style.fontShadow.getValue())
        }
    }

    private fun effects(): Collection<MobEffectInstance> {
        return mc.player?.activeEffects ?: emptyList()
    }

    private fun rows(preview: Boolean): List<Row> {
        if (preview) {
            return previewRows
        }

        val tickRate = mc.level?.tickRateManager()?.tickrate() ?: 20.0f
        return effects().map { effect ->
            Row(
                title = "${effect.effect.value().displayName.string} lv.${effect.amplifier + 1}",
                duration = durationText(effect, tickRate),
                effect = effect
            )
        }
    }

    private fun durationText(effect: MobEffectInstance, tickRate: Float): String {
        if (effect.isInfiniteDuration) {
            return "--"
        }
        val seconds = (effect.duration / tickRate).toInt()
        return "${seconds / 60}min${seconds % 60}s"
    }

    private data class Row(
        val title: String,
        val duration: String,
        val effect: MobEffectInstance?
    )

    companion object {
        private const val ROW_HEIGHT = 32
        private const val ICON_X = 8
        private const val ICON_Y = 7
        private const val ICON_SIZE = 18
        private const val TEXT_X = 34
        private const val PADDING_RIGHT = 10

        private val previewRows = listOf(
            Row("Speed lv.2", "1:23", null),
            Row("Strength lv.1", "0:42", null)
        )
    }
}
