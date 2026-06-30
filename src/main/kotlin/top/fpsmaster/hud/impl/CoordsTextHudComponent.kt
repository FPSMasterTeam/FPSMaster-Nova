package top.fpsmaster.hud.impl

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.CoordsDisplay

class CoordsTextHudComponent : HudComponent(
    id = "coords_text",
    x = 10f,
    y = 128f
) {
    override fun shouldRender(): Boolean = visible && CoordsDisplay.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val player = mc.player
        if (!CoordsDisplay.limitDisplay.getValue() || (!preview && player == null)) {
            CoordsDisplay.style.drawText(guiGraphics, resolveText(preview), 0, 0, 0xFFFFFFFF.toInt())
            return
        }

        val blockX = if (preview) 0 else player!!.blockX
        val blockY = if (preview) 64 else player!!.blockY
        val blockZ = if (preview) 0 else player!!.blockZ
        val remaining = CoordsDisplay.limitDisplayY.getValue().toInt() - blockY
        val prefix = "X:$blockX Y:$blockY("
        val value = remaining.toString()
        val suffix = ") Z:$blockZ"
        val text = prefix + value + suffix
        val valueColor = when {
            remaining < 5 -> 0xFFFF5555.toInt()
            remaining < 10 -> 0xFFFFFF55.toInt()
            else -> 0xFF55FF55.toInt()
        }

        CoordsDisplay.style.fillBackground(guiGraphics, -2, 0, mc.font.width(text) + 2, mc.font.lineHeight)
        guiGraphics.drawString(mc.font, prefix, 0, 0, 0xFFFFFFFF.toInt(), CoordsDisplay.style.fontShadow.getValue())
        val valueX = mc.font.width(prefix)
        guiGraphics.drawString(mc.font, value, valueX, 0, valueColor, CoordsDisplay.style.fontShadow.getValue())
        guiGraphics.drawString(mc.font, suffix, valueX + mc.font.width(value), 0, 0xFFFFFFFF.toInt(), CoordsDisplay.style.fontShadow.getValue())
    }

    private fun resolveText(preview: Boolean): String {
        if (preview) {
            return if (CoordsDisplay.limitDisplay.getValue()) "X:0 Y:64(28) Z:0" else "X:0 Y:64 Z:0"
        }

        val player = mc.player ?: return ""
        val base = "X:${player.blockX} Y:${player.blockY} Z:${player.blockZ}"
        if (!CoordsDisplay.limitDisplay.getValue()) {
            return base
        }

        val remaining = CoordsDisplay.limitDisplayY.getValue().toInt() - player.blockY
        return "X:${player.blockX} Y:${player.blockY}($remaining) Z:${player.blockZ}"
    }
}
