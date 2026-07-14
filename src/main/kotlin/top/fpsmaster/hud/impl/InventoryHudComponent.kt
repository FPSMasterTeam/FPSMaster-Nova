package top.fpsmaster.hud.impl

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.InventoryDisplay

class InventoryHudComponent : HudComponent(
    id = "inventory",
    x = 10f,
    y = 200f
) {
    override fun shouldRender(): Boolean = visible && InventoryDisplay.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize = HudSize(width = 164f, height = 64f)

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        InventoryDisplay.style.fillBackground(guiGraphics, -2, 0, 162, 64)
        if (preview) {
            return
        }

        val inventory = mc.player?.inventory ?: return
        for (slot in 9 until 36) {
            val index = slot - 9
            val x = (index % 9) * 18
            val y = (index / 9) * 20
            val itemStack = inventory.getItem(slot)
            if (!itemStack.isEmpty) {
                guiGraphics.renderItem(itemStack, x, y)
                guiGraphics.renderItemDecorations(mc.font, itemStack, x, y)
            }
        }
    }
}
