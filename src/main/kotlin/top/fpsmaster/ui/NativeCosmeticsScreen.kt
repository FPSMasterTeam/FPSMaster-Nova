package top.fpsmaster.ui

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.client.gui.screens.Screen
//? if <26 {
import net.minecraft.client.gui.screens.inventory.InventoryScreen
//?}
import net.minecraft.network.chat.Component
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.mc
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.render.DragonWings
import top.fpsmaster.prism.screen.CosmeticsBridge
import top.fpsmaster.prism.screen.SharedCosmetics
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen

class NativeCosmeticsScreen(private val parent: Screen?) : ToolkitScreen(Component.literal("Cosmetics")) {
    private val gui = SharedCosmetics()
    private val bridge = NovaCosmeticsBridge()
    private var preview = FloatArray(5)

    override fun renderUi(ui: UiFrame) {
        if (gui.draw(ui, bridge)) closeToParent()
    }

    //? if >=26 {
    /*override fun extractRenderState(g: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)
    }
    *///?}
    //? if >=1.20 && <26 {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val player = mc.player ?: return
        if (preview[2] <= 0f) return
        DragonWings.setPreviewing(true)
        try {
            val x = preview[0].toInt()
            val y = preview[1].toInt()
            val w = preview[2].toInt()
            val h = preview[3].toInt()
            //? if >=1.21.1 {
            InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, x, y, x + w, y + h,
                (h * 0.42f).toInt(), 0.0625f, previewLookX(), 0f, player)
            //?} else {
            /*InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, x + w / 2, y + h - 24,
                (h * 0.31f).toInt(), previewLookX(), 0f, player)
            *///?}
        } finally {
            DragonWings.setPreviewing(false)
        }
    }
    //?}

    private fun previewLookX(): Float =
        kotlin.math.sin(Math.toRadians(preview[4].toDouble())).toFloat() * 28f

    override fun handleEscape(): Boolean { closeToParent(); return true }

    private fun closeToParent() {
        ConfigManager.saveDefault()
        mc.setScreenCompat(parent)
    }

    private inner class NovaCosmeticsBridge : CosmeticsBridge {
        override fun i18n(key: String): String = Language.get(key)
        override fun playerName(): String = mc.player?.name?.string ?: "Steve"
        override fun capeEnabled(): Boolean = ModuleManager.modules["wavy-cape"]?.enabled == true
        override fun setCapeEnabled(enabled: Boolean) { ModuleManager.modules["wavy-cape"]?.enabled = enabled }
        override fun wingsEnabled(): Boolean = ModuleManager.modules["dragon-wings"]?.enabled == true
        override fun setWingsEnabled(enabled: Boolean) { ModuleManager.modules["dragon-wings"]?.enabled = enabled }
        override fun wingScale(): Float = (DragonWings.scale.getValue() / 100.0).toFloat()
        override fun setWingScale(scale: Float) { DragonWings.scale.setValue((scale * 100.0).coerceIn(0.0, 100.0)) }
        override fun paintPlayerPreview(ui: UiFrame, x: Float, y: Float, w: Float, h: Float, yaw: Float) {
            preview = floatArrayOf(x, y, w, h, yaw)
        }
    }
}
