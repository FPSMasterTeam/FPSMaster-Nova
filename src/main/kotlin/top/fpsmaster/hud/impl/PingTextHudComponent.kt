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
import top.fpsmaster.module.impl.ui.PingDisplay

class PingTextHudComponent : HudComponent(
    id = "ping_text",
    x = 10f,
    y = 146f
) {
    override fun shouldRender(): Boolean = visible && PingDisplay.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        PingDisplay.style.drawText(guiGraphics, resolveText(preview), 0, 0, PingDisplay.textColorValue())
    }

    private fun resolveText(preview: Boolean): String {
        if (preview) {
            return "Ping: 32ms"
        }

        val player = mc.player ?: return ""
        val ping = mc.connection?.getPlayerInfo(player.uuid)?.latency ?: 0
        return "Ping: ${ping}ms"
    }
}
