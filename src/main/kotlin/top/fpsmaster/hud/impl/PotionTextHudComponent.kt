package top.fpsmaster.hud.impl

//? if >=1.21.5 {
import net.minecraft.client.gui.Gui
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
//? if >=1.21.5 {
import net.minecraft.client.renderer.RenderPipelines
//?}
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
    private val effectAnimations = HashMap<String, Float>()
    private var lastNanos = 0L
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
        val dt = frameDt()
        val seen = HashSet<String>()
        rows(preview).forEachIndexed { index, row ->
            val key = row.title
            val exiting = row.effect != null && row.effect.duration <= EXIT_TICKS
            val visible = if (PotionDisplay.betterAnimation.getValue()) {
                visibleProgress(key, exiting, dt)
            } else {
                1f
            }
            if (visible <= 0.01f) {
                return@forEachIndexed
            }
            seen.add(key)
            val ox = if (PotionDisplay.betterAnimation.getValue()) {
                ((1f - visible) * -6f).toInt()
            } else {
                0
            }
            val y = index * (ROW_HEIGHT + spacing)
            val width = maxOf(mc.font.width(row.title), mc.font.width(row.duration)) + TEXT_X + PADDING_RIGHT
            PotionDisplay.style.fillBackground(guiGraphics, ox, y, ox + width, y + ROW_HEIGHT)
            if (PotionDisplay.betterAnimation.getValue() && PotionDisplay.style.background.getValue()) {
                row.effect?.let { effect ->
                    guiGraphics.fill(ox, y, ox + 2, y + ROW_HEIGHT, accentColor(effect))
                }
            }
            row.effect?.let { effect ->
                //? if >=26 {
                /*guiGraphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    net.minecraft.client.gui.Hud.getMobEffectSprite(effect.effect),
                    ICON_X + ox,
                    y + ICON_Y,
                    ICON_SIZE,
                    ICON_SIZE
                )*/
                //?} else if >=1.21.5 {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect.effect), ICON_X + ox, y + ICON_Y, ICON_SIZE, ICON_SIZE)
                //?}
                //? if <1.21.5 {
                /*guiGraphics.blit(ICON_X + ox, y + ICON_Y, 0, ICON_SIZE, ICON_SIZE, mc.getMobEffectTextures().get(effect.effect))
                *///?}
            } ?: guiGraphics.fill(ICON_X + ox, y + ICON_Y, ICON_X + ox + ICON_SIZE, y + ICON_Y + ICON_SIZE, 0x55FFFFFF)

            guiGraphics.drawString(mc.font, PotionDisplay.style.component(row.title), TEXT_X + ox, y + 5, 0xFFFFFFFF.toInt(), PotionDisplay.style.fontShadow.getValue())
            guiGraphics.drawString(mc.font, PotionDisplay.style.component(row.duration), TEXT_X + ox, y + 18, PotionDisplay.durationColor(row.seconds), PotionDisplay.style.fontShadow.getValue())
        }
        effectAnimations.keys.removeIf { it !in seen }
    }

    private fun frameDt(): Float {
        val now = System.nanoTime()
        val next = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = now
        return next
    }

    private fun visibleProgress(key: String, exiting: Boolean, dt: Float): Float {
        var current = effectAnimations.getOrPut(key) { if (exiting) 0f else 1f }
        current = if (exiting) {
            (current - dt / EXIT_SECONDS).coerceAtLeast(0f)
        } else {
            (current + dt / ENTER_SECONDS).coerceAtMost(1f)
        }
        effectAnimations[key] = current
        return if (exiting) current * current * current else {
            val t = 1f - current
            1f - t * t * t
        }
    }

    private fun accentColor(effect: MobEffectInstance): Int {
        //? if >=1.20.5 {
        val rgb = effect.effect.value().color
        //?} else {
        /*val rgb = effect.effect.color*/
        //?}
        return (150 shl 24) or (rgb and 0xFFFFFF)
    }

    private fun effects(): Collection<MobEffectInstance> {
        return mc.player?.activeEffects ?: emptyList()
    }

    private fun rows(preview: Boolean): List<Row> {
        if (preview) {
            return previewRows
        }

        //? if >=1.21.5 {
        val tickRate = mc.level?.tickRateManager()?.tickrate() ?: 20.0f
        //?} else {
        /*val tickRate = 20.0f
        *///?}
        return effects().map { effect ->
            Row(
                //? if >=1.20.5 {
                title = "${effect.effect.value().displayName.string} lv.${effect.amplifier + 1}",
                //?} else {
                /*title = "${effect.effect.displayName.string} lv.${effect.amplifier + 1}",
                *///?}
                duration = durationText(effect, tickRate),
                seconds = (effect.duration / tickRate).toInt(),
                effect = effect
            )
        }
    }

    private fun durationText(effect: MobEffectInstance, tickRate: Float): String {
        //? if >=1.20 {
        if (effect.isInfiniteDuration) {
            return "--"
        }
        //?}
        // <1.20 (1.19.2) has no infinite-duration effects.
        val seconds = (effect.duration / tickRate).toInt()
        return "${seconds / 60}min${seconds % 60}s"
    }

    private data class Row(
        val title: String,
        val duration: String,
        val seconds: Int = Int.MAX_VALUE,
        val effect: MobEffectInstance?
    )

    companion object {
        private const val ROW_HEIGHT = 32
        private const val ICON_X = 8
        private const val ICON_Y = 7
        private const val ICON_SIZE = 18
        private const val TEXT_X = 34
        private const val PADDING_RIGHT = 10
        private const val EXIT_TICKS = 8
        private const val ENTER_SECONDS = 0.20f
        private const val EXIT_SECONDS = 0.12f
        private val previewRows = listOf(
            Row("Speed lv.2", "1:23", 83, null),
            Row("Strength lv.1", "0:42", 42, null)
        )
    }
}
