package top.fpsmaster.hud

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
import top.fpsmaster.mc
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.prism.hud.HudEditorBridge
import top.fpsmaster.prism.hud.SharedHudEditor
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen
import top.fpsmaster.module.impl.auxiliary.ClientSettings

/** Nova host for the shared Prism HUD editor. */
class HudEditorScreen : ToolkitScreen(Component.literal("HUD Editor")) {
    private val editor = SharedHudEditor()
    private var graphics: GuiGraphics? = null
    private val bridge = NovaHudEditorBridge()
    private var closed = false

    override fun renderUi(ui: UiFrame) {
        graphics = (ui.canvas() as top.fpsmaster.ui.kit.NovaCanvas).graphics()
        editor.draw(ui, bridge)
        graphics = null
    }

    override fun handleEscape(): Boolean {
        editor.close(bridge)
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    override fun removed() {
        if (!closed) HudConfigManager.save()
        super.removed()
    }

    private inner class NovaHudEditorBridge : HudEditorBridge {
        override fun i18n(key: String): String {
            val translated = Language.get(key)
            if (translated != key) return translated
            return when (key) {
                "hud.editor.title" -> "HUD Editor"
                "hud.editor.done" -> "Done"
                else -> key
            }
        }

        override fun items(): List<HudEditorBridge.Item> {
            val hudScale = ClientSettings.hudRenderScale()
            val hudWidth = width / hudScale
            val hudHeight = height / hudScale
            val viewport = viewport(hudWidth, hudHeight)
            HudManager.components.values.forEach { it.adaptToSurface(hudWidth, hudHeight, preview = true) }
            return HudManager.components.values
            .filter { it.shouldRenderInEditor() }
            .map { component ->
                val size = component.measure(preview = true)
                HudEditorBridge.Item(
                    component.id,
                    component.id.replace('_', ' '),
                    viewport.x + component.x * viewport.scale,
                    viewport.y + component.y * viewport.scale,
                    size.width * viewport.scale,
                    size.height * viewport.scale,
                    component.scale,
                    HudComponent.MIN_SCALE,
                    HudComponent.MAX_SCALE,
                    true
                )
            }
        }

        override fun paintPreview(id: String, x: Float, y: Float, scale: Float) {
            val component = HudManager.components[id] ?: return
            val hudScale = ClientSettings.hudRenderScale()
            val hudWidth = width / hudScale
            val hudHeight = height / hudScale
            val viewport = viewport(hudWidth, hudHeight)
            graphics?.let {
                component.renderAt(it, x, y, scale * viewport.scale, preview = true)
            }
        }

        override fun setPlacement(id: String, x: Float, y: Float, scale: Float, surfaceWidth: Float, surfaceHeight: Float) {
            val component = HudManager.components[id] ?: return
            val hudScale = ClientSettings.hudRenderScale()
            val hudWidth = surfaceWidth / hudScale
            val hudHeight = surfaceHeight / hudScale
            val viewport = viewport(hudWidth, hudHeight)
            component.place(
                (x - viewport.x) / viewport.scale,
                (y - viewport.y) / viewport.scale,
                scale,
                hudWidth,
                hudHeight,
                preview = true
            )
        }

        override fun disable(id: String) {
            val component = HudManager.components[id] ?: return
            component.visible = false
            moduleId(id)?.let { ModuleManager.modules[it]?.enabled = false }
        }

        override fun save() = HudConfigManager.save()

        override fun close() {
            closed = true
            mc.setScreenCompat(null)
        }

        private fun moduleId(componentId: String): String? = when (componentId) {
            "fps_text" -> "fps-display"
            "cps_text" -> "cps-display"
            "coords_text" -> "coords-display"
            "direction_text" -> "direction-display"
            "combo_text" -> "combo-display"
            "reach_text" -> "reach-display"
            "ping_text" -> "ping-display"
            "armor" -> "armor-display"
            "inventory" -> "inventory-display"
            "item_count" -> "item-count-display"
            "keystrokes" -> "keystrokes"
            "mini_map" -> "mini-map"
            "mods_list" -> "mods-list"
            "player_display" -> "player-display"
            "potion_text" -> "potion-display"
            "scoreboard" -> "scoreboard"
            "target_hud" -> "target-display"
            "block_indicator" -> "block-indicator"
            "lyrics" -> "lyrics-display"
            else -> null
        }

        private fun viewport(hudWidth: Float, hudHeight: Float): Viewport {
            val contentHeight = height - SharedHudEditor.CONTENT_TOP
            val scale = minOf(width / hudWidth, contentHeight / hudHeight)
            return Viewport(
                (width - hudWidth * scale) / 2f,
                SharedHudEditor.CONTENT_TOP + (contentHeight - hudHeight * scale) / 2f,
                scale
            )
        }
    }

    private data class Viewport(val x: Float, val y: Float, val scale: Float)
}
