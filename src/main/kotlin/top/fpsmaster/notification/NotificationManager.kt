package top.fpsmaster.notification

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.mc
import top.fpsmaster.screenCompat
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.prism.input.FrameInput
import top.fpsmaster.prism.overlay.NotificationCenter
import top.fpsmaster.prism.theme.Theme
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.ui.kit.NovaCanvas
import top.fpsmaster.ui.kit.NovaBlur
import top.fpsmaster.ui.kit.NovaHost

/** Nova event adapter; notification behavior and rendering live in Prism. */
object NotificationManager {
    private val center = NotificationCenter()
    private val input = FrameInput()

    fun add(
        title: String,
        description: String,
        durationSeconds: Float = 2f,
        type: NotificationCenter.Type = NotificationCenter.Type.INFO
    ) {
        center.add(title, description, type, durationSeconds)
    }

    /** Painted by [top.fpsmaster.ui.kit.ToolkitScreen] on top of Prism screens. */
    fun paint(ui: UiFrame) {
        center.paint(ui)
    }

    @JvmStatic
    fun render(guiGraphics: GuiGraphics) {
        // A Prism screen paints the notifications itself, above its own content; drawing them here
        // as well would show them twice (once hidden under the screen's veil).
        if (mc.screenCompat is top.fpsmaster.ui.kit.ToolkitScreen) {
            return
        }
        val width = mc.window.guiScaledWidth.toFloat()
        val height = mc.window.guiScaledHeight.toFloat()
        val host = NovaHost(NovaCanvas(guiGraphics, mc.font), input, mc.font, width, height)
        val light = ClientSettings.lightTheme()
        center.paint(UiFrame(host, Theme.of(light, NovaBlur.enabled())))
        input.endFrame()
    }
}
