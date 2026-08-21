package top.fpsmaster.ui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.mc
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.NovaImage
import top.fpsmaster.ui.kit.ToolkitScreen
import top.fpsmaster.prism.screen.BackgroundsBridge
import top.fpsmaster.prism.screen.SharedBackgrounds
import top.fpsmaster.prism.theme.Argb
import top.fpsmaster.prism.widget.UiFrame
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class NativeBackgroundScreen(
    private val parent: net.minecraft.client.gui.screens.Screen?
) : ToolkitScreen(Component.literal("Background")) {
    private val gui = SharedBackgrounds()
    private val bridge = NovaBackgroundsBridge()

    override fun renderBackground(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        MainMenuBackgroundRenderer.render(guiGraphics, width, height, partialTick)
    }

    override fun renderUi(ui: UiFrame) {
        if (gui.draw(ui, bridge)) {
            ConfigManager.saveActive()
            mc.setScreenCompat(parent)
        }
    }

    override fun handleEscape(): Boolean {
        ConfigManager.saveActive()
        mc.setScreenCompat(parent)
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    private inner class NovaBackgroundsBridge : BackgroundsBridge {
        override fun i18n(key: String): String = Language.get(key)
        override fun selected(): String = ConfigManager.background

        override fun select(id: String) {
            ConfigManager.setBackground(id)
        }

        override fun pickCustom() {
            val dialog = FileDialog(null as Frame?, Language.get("backgroundselector.filedialog.title"), FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            }
            dialog.isVisible = true
            val file = dialog.file ?: return
            val dir = dialog.directory ?: return
            val source = java.io.File(dir, file).toPath()
            val dest = MainMenuBackgroundRenderer.customBackgroundFile()
            Files.createDirectories(dest.parent)
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
            ConfigManager.setBackground("custom")
        }

        override fun hasCustom(): Boolean = MainMenuBackgroundRenderer.hasCustomImage()

        override fun paintPreview(ui: UiFrame, id: String, x: Float, y: Float, w: Float, h: Float) {
            when (id) {
                "classic" -> ui.canvas().fillRoundRect(x, y, w, h, 5f, classicRgb())
                "shader" -> ui.canvas().fillGradientV(x, y, w, h, 0xFF1A3A6E.toInt(), 0xFF264D8C.toInt())
                "custom" -> {
                    if (!MainMenuBackgroundRenderer.prepareCustom()) {
                        ui.canvas().fillRoundRect(x, y, w, h, 5f, Argb.rgb(100, 150, 100))
                        val label = i18n("backgroundselector.preview.image")
                        val font = ui.font(12)
                        ui.canvas().drawString(font, label, x + (w - font.measure(label)) / 2f, y + h / 2f - 4f, 0xFFFFFFFF.toInt())
                        return
                    }
                    val img = NovaImage(
                        MainMenuBackgroundRenderer.customTextureId(),
                        MainMenuBackgroundRenderer.customTextureWidth(),
                        MainMenuBackgroundRenderer.customTextureHeight()
                    )
                    ui.canvas().drawImage(img, x, y, w, h, -1)
                }
                else -> {
                    val style = if (id == "panorama") "panorama_1" else id
                    val img = NovaImage(
                        top.fpsmaster.identifier("textures/gui/title/background/${style}_0.png"),
                        512,
                        512
                    )
                    ui.canvas().drawImage(img, x, y, w, h, -1)
                }
            }
        }

        override fun classicHue(): Float = ConfigManager.classicBackgroundHue
        override fun classicSaturation(): Float = ConfigManager.classicBackgroundSaturation
        override fun classicBrightness(): Float = ConfigManager.classicBackgroundBrightness
        override fun classicAlpha(): Float = ConfigManager.classicBackgroundAlpha
        override fun classicMode(): String = ConfigManager.classicBackgroundMode

        override fun setClassic(hue: Float, saturation: Float, brightness: Float, alpha: Float, mode: String) {
            ConfigManager.setClassicBackground(hue, saturation, brightness, alpha, mode)
        }

        private fun classicRgb(): Int {
            val rgb = Color.HSBtoRGB(
                ConfigManager.classicBackgroundHue,
                ConfigManager.classicBackgroundSaturation,
                ConfigManager.classicBackgroundBrightness
            ) and 0x00FFFFFF
            val a = (ConfigManager.classicBackgroundAlpha.coerceIn(0f, 1f) * 255f).toInt()
            return (a shl 24) or rgb
        }
    }
}
