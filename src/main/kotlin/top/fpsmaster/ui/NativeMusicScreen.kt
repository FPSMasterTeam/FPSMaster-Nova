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
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.music.MusicSource
import top.fpsmaster.music.Track
import top.fpsmaster.ui.kit.NovaMusicTextures
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class NativeMusicScreen(
    private val parent: net.minecraft.client.gui.screens.Screen?
) : ToolkitScreen(Component.literal("Music")) {
    private val gui = SharedMusic()
    private val bridge = NovaMusicBridge()

    override fun renderUi(ui: UiFrame) {
        if (gui.draw(ui, bridge)) {
            ConfigManager.saveActive()
            mc.setScreenCompat(parent)
        }
    }

    override fun handleEscape(): Boolean {
        if (gui.cancelOverlay()) return true
        ConfigManager.saveActive()
        mc.setScreenCompat(parent)
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    private class NovaMusicBridge : MusicBridge {
        @Volatile private var local = emptyList<Track>()
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
        override fun playbackMode(): MusicBridge.PlaybackMode = MusicController.playbackMode
        override fun setPlaybackMode(mode: MusicBridge.PlaybackMode) {
            MusicController.playbackMode = mode
            ConfigManager.setMusicPlaybackMode(mode.name)
        }
        override fun play(index: Int) = MusicController.play(index)

        override fun paintCover(ui: UiFrame, x: Float, y: Float, size: Float) {
            val image = NovaMusicTextures.cover(MusicController.coverUrl()) ?: return
            ui.canvas().drawImage(image, x, y, size, size, -1)
        }

        override fun tracks(): List<MusicBridge.TrackRow> = MusicController.snapshotTracks().map { track ->
            val sec = (track.durationMs / 1000L).toInt()
            val dur = "%d:%02d".format(sec / 60, sec % 60)
            MusicBridge.TrackRow(track.name, track.artists, dur, track.vip)
        }
        override fun localTracks(): List<MusicBridge.TrackRow> = local.map { track ->
            MusicBridge.TrackRow(track.name, track.artists, "0:00", false)
        }
        override fun importLocalMusic() {
            Thread({
                val dialog = FileDialog(null as Frame?, "选择本地音乐", FileDialog.LOAD).apply {
                    isMultipleMode = true
                    filenameFilter = java.io.FilenameFilter { _, name -> isLocalAudio(name) }
                    isVisible = true
                }
                val selected = dialog.files
                dialog.dispose()
                if (selected.isEmpty()) return@Thread
                mc.execute { local = selected.mapNotNull(::localTrack) }
            }, "FPSMaster-Music-Picker").apply { isDaemon = true }.start()
        }
        override fun playLocal(index: Int) = MusicController.playLocal(local, index)

        override fun listTitle(): String = MusicController.listTitle
        override fun search(query: String) = MusicController.search(query)
        override fun loadDiscover() = MusicController.loadDiscover()
        override fun loadPlaylists() = MusicController.loadPlaylists()
        override fun playlists(): Boolean = MusicController.snapshotPlaylists().isNotEmpty()
        override fun openPlaylist(index: Int) = MusicController.openPlaylist(index)
        override fun playlistRows(): List<MusicBridge.PlaylistRow> =
            MusicController.snapshotPlaylists().map { MusicBridge.PlaylistRow(it.name, it.trackCount.toString()) }

        override fun hasLyrics(): Boolean = MusicController.hasLyrics()
        override fun currentLyricIndex(): Int = MusicController.currentLyricLine()
        override fun lyricRows(): List<MusicBridge.LyricRow> = MusicController.lyric()?.lines?.map {
            MusicBridge.LyricRow(it.text, it.translation)
        } ?: emptyList()
        override fun lyricsHudEnabled(): Boolean = ModuleManager.modules["lyrics-display"]?.enabled == true
        override fun setLyricsHudEnabled(enabled: Boolean) {
            (ModuleManager.modules["lyrics-display"] as? LyricsDisplay)?.enabled = enabled
        }
        override fun lyricFontSize(): Float = LyricsDisplay.fontSize.getValue().toFloat()
        override fun setLyricFontSize(size: Float) = LyricsDisplay.fontSize.setValue(size.toDouble())
        override fun lyricLines(): Int = LyricsDisplay.lines.getValue().toInt()
        override fun setLyricLines(lines: Int) = LyricsDisplay.lines.setValue(lines.toDouble())
        override fun lyricTranslation(): Boolean = LyricsDisplay.translation.getValue()
        override fun setLyricTranslation(enabled: Boolean) = LyricsDisplay.translation.setValue(enabled)
        override fun lyricScroll(): Boolean = LyricsDisplay.scroll.getValue()
        override fun setLyricScroll(enabled: Boolean) = LyricsDisplay.scroll.setValue(enabled)
        override fun lyricBackground(): Boolean = LyricsDisplay.background.getValue()
        override fun setLyricBackground(enabled: Boolean) = LyricsDisplay.background.setValue(enabled)

        private fun localTrack(file: File): Track? {
            if (!file.isFile || !isLocalAudio(file.name)) return null
            return Track(MusicSource.NETEASE, file.toURI().toString(), name = file.nameWithoutExtension,
                artists = "本地音乐", coverUrl = localCover(file)?.toURI()?.toString())
        }

        private fun localCover(audio: File): File? {
            val files = audio.parentFile?.listFiles()?.associateBy { it.name.lowercase(java.util.Locale.ROOT) }
                ?: return null
            val base = audio.nameWithoutExtension.lowercase(java.util.Locale.ROOT)
            return listOf(base, "cover", "folder").firstNotNullOfOrNull { name ->
                listOf("png", "jpg", "jpeg").firstNotNullOfOrNull { extension -> files["$name.$extension"] }
            }
        }

        private fun isLocalAudio(name: String): Boolean {
            val lower = name.lowercase(java.util.Locale.ROOT)
            return lower.endsWith(".mp3") || lower.endsWith(".wav") ||
                lower.endsWith(".aiff") || lower.endsWith(".aif")
        }
    }
}
