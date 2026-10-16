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
import top.fpsmaster.notification.NotificationManager
import top.fpsmaster.prism.overlay.NotificationCenter
import top.fpsmaster.ui.kit.NativeDialogs
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class NativeBackgroundScreen(
    private val parent: net.minecraft.client.gui.screens.Screen?
) : ToolkitScreen(Component.literal("Background")) {
    private val gui = SharedBackgrounds()
    private val bridge = NovaBackgroundsBridge()

    override fun renderToolkitBackground(guiGraphics: GuiGraphics, partialTick: Float) {
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

        // Called from Prism's draw (inside Screen.render): the dialog runs on a worker thread and the
        // copy happens in the render-thread callback, so nothing here can throw into the render stack.
        override fun pickCustom() {
            NativeDialogs.pickFiles(
                Language.get("backgroundselector.filedialog.title"),
                listOf("*.png", "*.jpg", "*.jpeg"),
                Language.get("backgroundselector.filedialog.filter"),
                multiple = false
            ) { selected ->
                val source = selected.firstOrNull() ?: return@pickFiles
                try {
                    val dest = MainMenuBackgroundRenderer.customBackgroundFile()
                    Files.createDirectories(dest.parent)
                    Files.copy(source.toPath(), dest, StandardCopyOption.REPLACE_EXISTING)
                    ConfigManager.setBackground("custom")
                } catch (exception: Exception) {
                    top.fpsmaster.logger.warn("Failed to import custom background {}", source, exception)
                    NotificationManager.add(
                        Language.get("backgroundselector.filedialog.title"),
                        Language.get("backgroundselector.import.failed"),
                        3f,
                        NotificationCenter.Type.ERROR
                    )
                }
            }
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
