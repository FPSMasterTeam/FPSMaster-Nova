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
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.module.impl.ui.LyricsDisplay
import top.fpsmaster.musicui.MusicController
import top.fpsmaster.prism.input.FrameInput
import top.fpsmaster.prism.screen.MusicBridge
import top.fpsmaster.prism.screen.SharedLyrics
import top.fpsmaster.prism.theme.Theme
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.ui.kit.NovaCanvas
import top.fpsmaster.ui.kit.NovaHost

class LyricsHudComponent : HudComponent("lyrics", 20f, 140f) {
    private val renderer = SharedLyrics()
    private val input = FrameInput()
    private var lastNanos = 0L
    private var cachedLyric: top.fpsmaster.music.Lyric? = null
    private var cachedRows = emptyList<MusicBridge.LyricRow>()

    override fun shouldRender(): Boolean = visible && LyricsDisplay.isActive() && MusicController.lyric() != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize = HudSize(260f, SharedLyrics.hudHeight(style()))

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val rows = if (preview) {
            listOf(
                MusicBridge.LyricRow("We're singing through the night", "我们在夜色中歌唱"),
                MusicBridge.LyricRow("This moment stays with us", "这一刻与我们同在")
            )
        } else {
            rows()
        }
        val current = if (preview) 0 else MusicController.currentLyricLine()
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = now
        val size = measure(preview)
        val canvas = NovaCanvas(guiGraphics, mc.font)
        val host = NovaHost(canvas, input, mc.font, size.width, size.height)
        val theme = Theme.of(ClientSettings.theme.getValue().toInt() == 1, ClientSettings.blur.getValue())
        renderer.drawHud(UiFrame(host, theme), rows, current, 0f, 0f, size.width, style(), dt)
        input.endFrame()
    }

    private fun style() = SharedLyrics.HudStyle(
        LyricsDisplay.fontSize.getValue().toInt(),
        LyricsDisplay.lines.getValue().toInt(),
        LyricsDisplay.translation.getValue(),
        LyricsDisplay.scroll.getValue(),
        LyricsDisplay.background.getValue(),
        LyricsDisplay.backgroundColor.argb(),
        LyricsDisplay.textColor.argb()
    )

    private fun rows(): List<MusicBridge.LyricRow> {
        val lyric = MusicController.lyric()
        if (lyric === cachedLyric) return cachedRows
        cachedLyric = lyric
        cachedRows = lyric?.lines?.map { MusicBridge.LyricRow(it.text, it.translation) } ?: emptyList()
        return cachedRows
    }
}
