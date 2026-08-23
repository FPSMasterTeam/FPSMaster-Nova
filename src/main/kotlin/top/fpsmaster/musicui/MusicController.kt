package top.fpsmaster.musicui

import top.fpsmaster.mc
import top.fpsmaster.music.MusicService
import top.fpsmaster.music.MusicSource
import top.fpsmaster.music.Lyric
import top.fpsmaster.music.PlaylistBrief
import top.fpsmaster.music.Track
import top.fpsmaster.music.store.MusicCredentialStore
import top.fpsmaster.config.ConfigManager
import java.util.concurrent.Executors

object MusicController {
    private val service = MusicService()
    private val engine = AudioEngine()
    private val store = MusicCredentialStore.default("FPSMaster")
    private val pool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fpsmaster-music").apply { isDaemon = true }
    }

    @Volatile
    var source: MusicSource = MusicSource.NETEASE
        private set

    @Volatile
    var status: String = ""
        private set

    @Volatile
    var listTitle: String = ""
        private set

    private val queue = mutableListOf<Track>()
    private val playlists = mutableListOf<PlaylistBrief>()
    private var index = -1

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
        lyric = null
    }

    fun loggedIn(): Boolean = if (qq()) {
        service.qq.musicid.isNotBlank() && service.qq.musicKey.isNotBlank()
    } else {
        service.netease.cookie.contains("MUSIC_U")
    }

    fun nowTitle(): String = current()?.name ?: "未在播放"

    fun nowArtist(): String = current()?.artists ?: if (qq()) "QQ 音乐" else "网易云音乐"

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
        if (queue.isEmpty()) {
            return
        }
        index = (index + 1).coerceAtMost(queue.lastIndex)
        if (index >= 0) {
            playTrack(queue[index])
        }
    }

    fun prev() {
        if (queue.isEmpty()) {
            return
        }
        index = (index - 1).coerceAtLeast(0)
        playTrack(queue[index])
    }

    fun play(i: Int) {
        if (i !in queue.indices) {
            return
        }
        index = i
        playTrack(queue[i])
    }

    fun snapshotTracks(): List<Track> = synchronized(queue) { queue.toList() }

    fun snapshotPlaylists(): List<PlaylistBrief> = synchronized(playlists) { playlists.toList() }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            return
        }
        status = "搜索中…"
        val src = source
        pool.execute {
            runCatching { service.search(src, q, 40) }
                .onSuccess { tracks ->
                    post {
                        synchronized(queue) {
                            queue.clear()
                            queue.addAll(tracks)
                        }
                        listTitle = "搜索：$q"
                        status = if (tracks.isEmpty()) "没有结果" else ""
                    }
                }
                .onFailure { err ->
                    post { status = err.message ?: "搜索失败" }
                }
        }
    }

    fun loadDiscover() {
        status = "加载中…"
        val src = source
        pool.execute {
            val result = runCatching {
                if (src == MusicSource.NETEASE) {
                    service.netease.getDailyRecommendSongs().ifEmpty { service.search(src, "热歌", 30) }
                } else {
                    service.search(src, "热歌", 30)
                }
            }
            result.onSuccess { tracks ->
                post {
                    synchronized(queue) {
                        queue.clear()
                        queue.addAll(tracks)
                    }
                    listTitle = if (src == MusicSource.NETEASE) "每日推荐" else "热歌"
                    status = ""
                }
            }.onFailure { err ->
                post { status = err.message ?: "加载失败" }
            }
        }
    }

    fun loadPlaylists() {
        if (source != MusicSource.NETEASE) {
            synchronized(playlists) { playlists.clear() }
            status = "QQ 歌单暂未接入"
            return
        }
        status = "加载歌单…"
        pool.execute {
            runCatching {
                val uid = service.netease.getLoginUid() ?: return@runCatching emptyList()
                service.netease.getUserPlaylists(uid)
            }.onSuccess { rows ->
                post {
                    synchronized(playlists) {
                        playlists.clear()
                        playlists.addAll(rows)
                    }
                    status = if (rows.isEmpty()) "" else ""
                    listTitle = "我的歌单"
                }
            }.onFailure { err ->
                post { status = err.message ?: "歌单加载失败" }
            }
        }
    }

    fun openPlaylist(i: Int) {
        val item = synchronized(playlists) { playlists.getOrNull(i) } ?: return
        status = "打开歌单…"
        pool.execute {
            runCatching { service.netease.getPlaylistTracks(item.id) }
                .onSuccess { tracks ->
                    post {
                        synchronized(queue) {
                            queue.clear()
                            queue.addAll(tracks)
                        }
                        listTitle = item.name
                        status = ""
                    }
                }
                .onFailure { err ->
                    post { status = err.message ?: "歌单打开失败" }
                }
        }
    }

    private fun current(): Track? = synchronized(queue) { queue.getOrNull(index) }

    private fun playTrack(track: Track) {
        status = "获取链接…"
        lyric = null
        pool.execute {
            runCatching { service.getLyric(track) }
                .onSuccess { loaded -> post { if (current() == track) lyric = loaded } }
        }
        pool.execute {
            runCatching { service.getSongUrl(track) }
                .onSuccess { url ->
                    if (!url.available) {
                        post { status = url.reason ?: "无法播放" }
                        return@onSuccess
                    }
                    val referer = if (track.source == MusicSource.QQ) "https://y.qq.com/" else null
                    post {
                        status = if (url.isTrial) "试听" else ""
                        engine.play(url.url, referer, track.durationMs) {
                            next()
                        }
                    }
                }
                .onFailure { err ->
                    post { status = err.message ?: "播放失败" }
                }
        }
    }

    private fun post(block: () -> Unit) {
        mc.execute(block)
    }
}
