package top.fpsmaster.musicui

import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.music.MusicApiException
import top.fpsmaster.music.MusicErrorKind
import top.fpsmaster.music.MusicService
import top.fpsmaster.music.MusicSource
import top.fpsmaster.music.Lyric
import top.fpsmaster.music.PlaylistBrief
import top.fpsmaster.music.Track
import top.fpsmaster.music.store.MusicCredentialStore
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.prism.screen.MusicBridge
import top.fpsmaster.translation.Language
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Music state behind the Prism music screen. Mutable state is only written on the client thread
 * (worker results come back through [post]); workers only call the platform APIs.
 *
 * What is playing ([nowPlaying]) is tracked on its own rather than derived from `queue[index]`:
 * searching or opening a playlist replaces the queue while the old song keeps playing.
 */
object MusicController {
    private val service = MusicService()
    private val engine = AudioEngine()
    private val store = MusicCredentialStore.default("FPSMaster")

    /**
     * Music gets its own bounded pool: one Cadence request can take connect + total + read timeout
     * (about 55 s) when a platform stalls, and an unbounded pool would pile up a thread per click.
     */
    private val pool = ThreadPoolExecutor(
        3, 3, 30L, TimeUnit.SECONDS, LinkedBlockingQueue(32)
    ) { runnable ->
        Thread(runnable, "fpsmaster-music").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    @Volatile
    var source: MusicSource = MusicSource.NETEASE
        private set

    @Volatile
    var status: String = ""
        private set

    @Volatile
    var listTitle: String = ""
        private set

    @Volatile
    var tracksState: MusicBridge.ListState = MusicBridge.ListState.READY
        private set

    @Volatile
    var tracksError: String = ""
        private set

    @Volatile
    var playlistsState: MusicBridge.ListState = MusicBridge.ListState.READY
        private set

    @Volatile
    var playlistsError: String = ""
        private set

    private val queue = mutableListOf<Track>()
    private val playlists = mutableListOf<PlaylistBrief>()

    /** Position of [nowPlaying] in [queue], or -1 when it came from a list that has been replaced. */
    private var index = -1

    @Volatile
    private var nowPlaying: Track? = null

    /** Bumped per list request / per play, so a slow response cannot overwrite a newer one. */
    private val listGeneration = AtomicInteger()
    private val playlistGeneration = AtomicInteger()
    private val playGeneration = AtomicInteger()

    var playbackMode: MusicBridge.PlaybackMode = runCatching {
        MusicBridge.PlaybackMode.valueOf(ConfigManager.musicPlaybackMode)
    }.getOrDefault(MusicBridge.PlaybackMode.SEQUENTIAL)

    @Volatile
    private var lyric: Lyric? = null

    init {
        store.load()
        service.netease.cookie = store.neteaseCookie
        service.qq.musicid = store.qqMusicId
        service.qq.musicKey = store.qqMusicKey
        engine.setVolume((ConfigManager.musicVolume / 100.0).toFloat())
    }

    fun qq(): Boolean = source == MusicSource.QQ

    fun setQq(qq: Boolean) {
        source = if (qq) MusicSource.QQ else MusicSource.NETEASE
        synchronized(queue) { queue.clear() }
        index = -1
        listTitle = ""
        status = ""
        listGeneration.incrementAndGet()
        playlistGeneration.incrementAndGet()
        tracksState = MusicBridge.ListState.READY
        playlistsState = MusicBridge.ListState.READY
    }

    /**
     * Stops playback and the worker pool. Called from the client shutdown hook: the audio engine
     * owns a javax.sound line plus its own decode thread, and the pool can still be draining a
     * search/lyric request, neither of which the JVM tears down on its own.
     */
    fun shutdown() {
        engine.stop()
        pool.shutdownNow()
    }

    fun loggedIn(): Boolean = if (qq()) {
        service.qq.musicid.isNotBlank() && service.qq.musicKey.isNotBlank()
    } else {
        service.netease.cookie.contains("MUSIC_U")
    }

    fun nowTitle(): String = current()?.name ?: Language.get("music.lyrics.idle")

    fun nowArtist(): String = current()?.artists
        ?: Language.get(if (qq()) "music.source.qq" else "music.source.netease")

    fun coverUrl(): String? = current()?.coverUrl

    fun playing(): Boolean = engine.isPlaying

    fun paused(): Boolean = engine.isPaused

    fun progress(): Float {
        val dur = durationMs().coerceAtLeast(1L)
        return (engine.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    }

    fun positionMs(): Long = engine.positionMs

    fun durationMs(): Long {
        val fromEngine = engine.durationMs
        if (fromEngine > 0) {
            return fromEngine
        }
        return current()?.durationMs ?: 0L
    }

    fun lyric(): Lyric? = lyric

    fun hasLyrics(): Boolean = current()?.id?.startsWith("file:") == false

    fun currentLyricLine(): Int {
        val rows = lyric?.lines ?: return -1
        val position = positionMs()
        var result = -1
        rows.forEachIndexed { i, line ->
            if (!line.isMetadata && line.startMs <= position) result = i
        }
        return result
    }

    fun volume(): Float = engine.volume

    fun setVolume(t: Float) {
        val volume = t.coerceIn(0f, 1f)
        engine.setVolume(volume)
        ConfigManager.setMusicVolume(volume * 100.0)
    }

    fun seek(t: Float) {
        engine.seek((t.coerceIn(0f, 1f) * durationMs()).toLong())
    }

    fun togglePause() {
        if (!engine.isActive) {
            val cur = current() ?: return
            playTrack(cur)
            return
        }
        engine.togglePause()
    }

    fun next() {
        val size = synchronized(queue) { queue.size }
        if (size == 0) {
            return
        }
        playAt(if (index < 0) 0 else (index + 1) % size)
    }

    fun prev() {
        val size = synchronized(queue) { queue.size }
        if (size == 0) {
            return
        }
        playAt(if (index < 0) 0 else (index - 1 + size) % size)
    }

    fun play(i: Int) {
        playAt(i)
    }

    fun playLocal(tracks: List<Track>, i: Int) {
        if (i !in tracks.indices) return
        replaceQueue(tracks)
        playAt(i)
    }

    private fun playAt(i: Int) {
        val track = synchronized(queue) { queue.getOrNull(i) } ?: return
        index = i
        playTrack(track)
    }

    /** Client thread. Re-finds the playing song in the new list so next/prev continue from it. */
    private fun replaceQueue(tracks: List<Track>) {
        synchronized(queue) {
            queue.clear()
            queue.addAll(tracks)
        }
        index = nowPlaying?.let { playing -> tracks.indexOfFirst { it.id == playing.id && it.source == playing.source } } ?: -1
    }

    fun snapshotTracks(): List<Track> = synchronized(queue) { queue.toList() }

    fun snapshotPlaylists(): List<PlaylistBrief> = synchronized(playlists) { playlists.toList() }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            return
        }
        val src = source
        loadTracks(Language.get("music.status.searchtitle").format(q)) { service.search(src, q, 40) }
    }

    /**
     * NetEase daily picks need a login. Nova has no music login screen (credentials come from the
     * shared store, e.g. signed in through Edge), so without one — or once the platform reports the
     * cookie expired — discover falls back to the hot list instead of a "sign in" dead end.
     */
    fun loadDiscover() {
        val src = source
        val daily = src == MusicSource.NETEASE && loggedIn()
        loadTracks(Language.get(if (daily) "music.status.daily" else "music.status.hot")) {
            if (!daily) {
                return@loadTracks service.search(src, HOT_KEYWORD, 30)
            }
            try {
                service.netease.getDailyRecommendSongs().ifEmpty { service.search(src, HOT_KEYWORD, 30) }
            } catch (expired: MusicApiException) {
                if (expired.kind != MusicErrorKind.NOT_LOGGED_IN) throw expired
                post { forgetNeteaseLogin() }
                service.search(src, HOT_KEYWORD, 30)
            }
        }
    }

    fun loadPlaylists() {
        val gen = playlistGeneration.incrementAndGet()
        if (source != MusicSource.NETEASE) {
            synchronized(playlists) { playlists.clear() }
            playlistsState = MusicBridge.ListState.ERROR
            playlistsError = Language.get("music.error.qqplaylists")
            return
        }
        if (!loggedIn()) {
            synchronized(playlists) { playlists.clear() }
            playlistsState = MusicBridge.ListState.NEED_LOGIN
            return
        }
        playlistsState = MusicBridge.ListState.LOADING
        playlistsError = ""
        submit({ failPlaylists(gen, null) }) {
            val result = runCatching {
                // null: the stored cookie is no longer valid.
                service.netease.getLoginUid()?.let { service.netease.getUserPlaylists(it) }
            }
            post {
                if (gen != playlistGeneration.get()) return@post
                result.onSuccess { rows ->
                    if (rows == null) {
                        forgetNeteaseLogin()
                        synchronized(playlists) { playlists.clear() }
                        playlistsState = MusicBridge.ListState.NEED_LOGIN
                        return@onSuccess
                    }
                    synchronized(playlists) {
                        playlists.clear()
                        playlists.addAll(rows)
                    }
                    playlistsState = MusicBridge.ListState.READY
                    listTitle = Language.get("music.status.myplaylists")
                }.onFailure { failPlaylists(gen, it) }
            }
        }
    }

    fun openPlaylist(i: Int) {
        val item = synchronized(playlists) { playlists.getOrNull(i) } ?: return
        loadTracks(item.name) { service.netease.getPlaylistTracks(item.id) }
    }

    /** Client thread. Runs [request] on the pool and publishes the result as the track list. */
    private fun loadTracks(title: String, request: () -> List<Track>) {
        val gen = listGeneration.incrementAndGet()
        tracksState = MusicBridge.ListState.LOADING
        tracksError = ""
        submit({ failTracks(gen, null) }) {
            val result = runCatching(request)
            post {
                if (gen != listGeneration.get()) return@post
                result.onSuccess { tracks ->
                    replaceQueue(tracks)
                    listTitle = title
                    tracksState = MusicBridge.ListState.READY
                }.onFailure { failTracks(gen, it) }
            }
        }
    }

    private fun failTracks(gen: Int, error: Throwable?) {
        if (gen != listGeneration.get()) return
        tracksState = stateFor(error)
        tracksError = describe(error)
    }

    private fun failPlaylists(gen: Int, error: Throwable?) {
        if (gen != playlistGeneration.get()) return
        if (error is MusicApiException && error.kind == MusicErrorKind.NOT_LOGGED_IN) {
            forgetNeteaseLogin()
        }
        playlistsState = stateFor(error)
        playlistsError = describe(error)
    }

    private fun stateFor(error: Throwable?): MusicBridge.ListState =
        if (error is MusicApiException && error.kind == MusicErrorKind.NOT_LOGGED_IN) MusicBridge.ListState.NEED_LOGIN
        else MusicBridge.ListState.ERROR

    /** Localized reason; null = the request never ran because the pool was saturated. */
    private fun describe(error: Throwable?): String {
        if (error == null) {
            return Language.get("music.error.busy")
        }
        if (error !is MusicApiException) {
            logger.warn("Music request failed", error)
            return Language.get("music.error.unknown")
        }
        logger.warn("Music request failed ({}): {}", error.kind, error.message)
        return Language.get("music.error." + error.kind.name.lowercase())
    }

    private fun forgetNeteaseLogin() {
        store.clearNetease()
        service.netease.cookie = ""
    }

    /** [onRejected] runs on the client thread when the bounded pool is full. */
    private fun submit(onRejected: () -> Unit, task: () -> Unit) {
        try {
            pool.execute(task)
        } catch (rejected: RejectedExecutionException) {
            onRejected()
        }
    }

    private fun current(): Track? = nowPlaying

    private fun playTrack(track: Track) {
        val gen = playGeneration.incrementAndGet()
        nowPlaying = track
        status = Language.get("music.status.fetching")
        lyric = null
        if (track.id.startsWith("file:")) {
            status = ""
            engine.playFile(File(java.net.URI.create(track.id)), track.durationMs, ::onEnded)
            return
        }
        submit({ status = describe(null) }) {
            runCatching { service.getLyric(track) }
                .onSuccess { loaded -> post { if (gen == playGeneration.get()) lyric = loaded } }
        }
        submit({ status = describe(null) }) {
            val result = runCatching { service.getSongUrl(track) }
            post {
                // A later click already started another song; this URL is for one nobody wants now.
                if (gen != playGeneration.get()) return@post
                result.onSuccess { url ->
                    if (!url.available) {
                        status = url.reason ?: Language.get("music.status.unplayable")
                        return@onSuccess
                    }
                    val referer = if (track.source == MusicSource.QQ) "https://y.qq.com/" else null
                    status = if (url.isTrial) Language.get("music.status.trial") else ""
                    engine.play(url.url, referer, track.durationMs) { onEnded() }
                }.onFailure { status = describe(it) }
            }
        }
    }

    private fun onEnded() {
        val size = synchronized(queue) { queue.size }
        if (size == 0 || index < 0) return
        when (playbackMode) {
            MusicBridge.PlaybackMode.REPEAT_ONE -> playAt(index)
            MusicBridge.PlaybackMode.SHUFFLE -> {
                var next = if (size == 1) index else ThreadLocalRandom.current().nextInt(size - 1)
                if (next >= index && size > 1) next++
                playAt(next)
            }
            MusicBridge.PlaybackMode.SEQUENTIAL -> if (index + 1 < size) {
                playAt(index + 1)
            }
        }
    }

    private const val HOT_KEYWORD = "热歌"

    private fun post(block: () -> Unit) {
        mc.execute(block)
    }
}
