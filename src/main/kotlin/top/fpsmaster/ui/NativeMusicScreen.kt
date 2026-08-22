package top.fpsmaster.ui

import net.minecraft.network.chat.Component
import top.fpsmaster.mc
import top.fpsmaster.musicui.MusicController
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen
import top.fpsmaster.prism.screen.MusicBridge
import top.fpsmaster.prism.screen.SharedMusic
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.ui.LyricsDisplay

class NativeMusicScreen(
    private val parent: net.minecraft.client.gui.screens.Screen?
) : ToolkitScreen(Component.literal("Music")) {
    private val gui = SharedMusic()
    private val bridge = NovaMusicBridge()

    override fun renderUi(ui: UiFrame) {
        if (gui.draw(ui, bridge)) {
            mc.setScreenCompat(parent)
        }
    }

    override fun handleEscape(): Boolean {
        mc.setScreenCompat(parent)
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    private class NovaMusicBridge : MusicBridge {
        override fun i18n(key: String): String = Language.get(key)
        override fun qq(): Boolean = MusicController.qq()
        override fun setQq(qq: Boolean) = MusicController.setQq(qq)
        override fun loggedIn(): Boolean = MusicController.loggedIn()
        override fun status(): String = MusicController.status
        override fun nowTitle(): String = MusicController.nowTitle()
        override fun nowArtist(): String = MusicController.nowArtist()
        override fun playing(): Boolean = MusicController.playing()
        override fun paused(): Boolean = MusicController.paused()
        override fun progress(): Float = MusicController.progress()
        override fun positionMs(): Long = MusicController.positionMs()
        override fun durationMs(): Long = MusicController.durationMs()
        override fun volume(): Float = MusicController.volume()
        override fun setVolume(t: Float) = MusicController.setVolume(t)
        override fun seek(t: Float) = MusicController.seek(t)
        override fun togglePause() = MusicController.togglePause()
        override fun next() = MusicController.next()
        override fun prev() = MusicController.prev()
        override fun play(index: Int) = MusicController.play(index)

        override fun tracks(): List<MusicBridge.TrackRow> = MusicController.snapshotTracks().map { track ->
            val sec = (track.durationMs / 1000L).toInt()
            val dur = "%d:%02d".format(sec / 60, sec % 60)
            MusicBridge.TrackRow(track.name, track.artists, dur, track.vip)
        }

        override fun listTitle(): String = MusicController.listTitle
        override fun search(query: String) = MusicController.search(query)
        override fun loadDiscover() = MusicController.loadDiscover()
        override fun loadPlaylists() = MusicController.loadPlaylists()
        override fun playlists(): Boolean = MusicController.snapshotPlaylists().isNotEmpty()
        override fun openPlaylist(index: Int) = MusicController.openPlaylist(index)
        override fun playlistRows(): List<MusicBridge.PlaylistRow> =
            MusicController.snapshotPlaylists().map { MusicBridge.PlaylistRow(it.name, it.trackCount.toString()) }

        override fun hasLyrics(): Boolean = true
        override fun currentLyricIndex(): Int = MusicController.currentLyricLine()
        override fun lyricRows(): List<MusicBridge.LyricRow> = MusicController.lyric()?.lines?.map {
            MusicBridge.LyricRow(it.text, it.translation)
        } ?: emptyList()
        override fun lyricsHudEnabled(): Boolean = ModuleManager.modules["lyrics-display"]?.enabled == true
        override fun setLyricsHudEnabled(enabled: Boolean) {
            (ModuleManager.modules["lyrics-display"] as? LyricsDisplay)?.enabled = enabled
        }
    }
}
