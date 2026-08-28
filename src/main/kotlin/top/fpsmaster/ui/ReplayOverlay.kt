package top.fpsmaster.ui

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hideGuiCompat
import top.fpsmaster.mc
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.module.impl.auxiliary.Replay
import top.fpsmaster.screenCompat
import top.fpsmaster.setScreenCompat
import top.fpsmaster.prism.input.FrameInput
import top.fpsmaster.prism.theme.Theme
import top.fpsmaster.prism.widget.Chrome
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.NovaBlur
import top.fpsmaster.ui.kit.NovaCanvas
import top.fpsmaster.ui.kit.NovaHost

/**
 * The transport bar drawn over the world while a recording is playing.
 *
 * Read-only on purpose: the world has no cursor, so anything clickable here would be unreachable.
 * It exists so the viewer can see where they are and knows the way back — Escape opens
 * [NativeReplayScreen], which has the buttons.
 */
object ReplayOverlay {

    /**
     * Its own input, never fed: [UiFrame] wants one, and sharing the HUD's or a screen's would let
     * this bar swallow a press meant for something else.
     */
    private val input = FrameInput()

    /**
     * Escape during playback: open the controls instead of the pause menu. Returns whether it took
     * over, which is what `MixinReplayPauseMenu` cancels on.
     *
     * Lives here rather than in the mixin because reaching the current screen is version-shaped
     * (26.2 moved it onto `Minecraft.gui`) and [screenCompat] already carries that difference.
     */
    @JvmStatic
    fun openControls(): Boolean {
        if (Replay.session == null || mc.screenCompat != null) {
            return false
        }
        mc.setScreenCompat(NativeReplayScreen(null))
        return true
    }

    @JvmStatic
    fun render(guiGraphics: GuiGraphics) {
        val session = Replay.session ?: return
        // 导出烤的是屏幕上的像素，这条控制条会被印进每一帧。
        if (session.isExporting) {
            return
        }
        // F1 藏 HUD 的时候也得藏：录像素材里不该有客户端自己的控制条。
        if (hideGuiCompat || mc.player == null) {
            return
        }
        val gw = mc.window.guiScaledWidth.toFloat()
        val gh = mc.window.guiScaledHeight.toFloat()
        if (gw <= 0f || gh <= 0f) {
            return
        }
        val host = NovaHost(NovaCanvas(guiGraphics, mc.font), input, mc.font, gw, gh)
        val ui = UiFrame(host, Theme.of(ClientSettings.lightTheme(), NovaBlur.enabled()))

        val w = minOf(PANEL_W, gw - 24f)
        val x = (gw - w) / 2f
        Chrome.panel(ui, x, PANEL_Y, w, PANEL_H)

        val duration = session.outputDuration()
        val clock = "${time(session.outputMillis)} / ${time(duration)}   x${"%.2f".format(session.playback.speed)}"
        val font = ui.font(10)
        ui.canvas().drawString(font, clock, x + 10f, PANEL_Y + 7f, ui.theme().textPrimary())
        if (session.playback.isPaused) {
            val paused = Language.get("replay.hud.paused")
            val small = ui.font(9)
            ui.canvas().drawString(
                small,
                paused,
                x + w - 10f - small.measure(paused),
                PANEL_Y + 8f,
                ui.theme().danger(),
            )
        }

        // 只画不收输入：showThumb=false 的那个重载不读鼠标，世界里也没有鼠标可读。
        val fraction = if (duration <= 0L) 0f else (session.outputMillis.toFloat() / duration).coerceIn(0f, 1f)
        Chrome.slider(ui, x + 10f, PANEL_Y + 21f, w - 20f, fraction, false)

        val hint = Language.get("replay.hud.hint")
        val hintFont = ui.font(9)
        ui.canvas().drawString(
            hintFont,
            hint,
            x + (w - hintFont.measure(hint)) / 2f,
            PANEL_Y + PANEL_H + 3f,
            ui.theme().textDisabled(),
        )

        input.endFrame()
    }

    private fun time(millis: Long): String {
        val total = millis / 1000L
        return "%d:%02d".format(total / 60L, total % 60L)
    }

    private const val PANEL_W = 300f
    private const val PANEL_Y = 8f
    private const val PANEL_H = 32f
}
