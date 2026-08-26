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
import top.fpsmaster.module.impl.ui.ServerAddressDisplay

class ServerAddressTextHudComponent : HudComponent(
    id = "server_address",
    x = 10f,
    y = 194f
) {
    override fun shouldRender(): Boolean = visible && ServerAddressDisplay.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val text = resolveText(preview)
        return HudSize(
            width = mc.font.width(text).toFloat().coerceAtLeast(1f),
            height = mc.font.lineHeight.toFloat()
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        ServerAddressDisplay.style.drawText(
            guiGraphics,
            resolveText(preview),
            0,
            0,
            ServerAddressDisplay.textColorValue()
        )
    }

    private fun resolveText(preview: Boolean): String {
        val address = if (preview) "mc.hypixel.net" else ServerAddressDisplay.currentAddress()
        val label = ServerAddressDisplay.label.getValue().trim()
        return if (label.isEmpty()) address else "$label $address"
    }
}
