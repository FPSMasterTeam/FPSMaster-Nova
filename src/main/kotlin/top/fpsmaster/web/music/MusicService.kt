package top.fpsmaster.web.music

/**
 * 统一门面：按 [MusicSource] 分发到具体平台客户端。
 *
 * 方便 LocalServer 用一个入口处理前端请求（前端只需带上 source 字段）。
 * 播放层与本类无关——本类只负责返回直链/元数据/歌词，实际解码播放由原生音频库完成。
 *
 * 每个平台客户端各自持有登录态；持久化/回填登录 cookie 请分别操作
 * [netease].cookie 与 [qq].musicid/[qq].musicKey。
 */
class MusicService(
    val netease: NeteaseMusicApi = NeteaseMusicApi(),
    val qq: QQMusicApi = QQMusicApi(),
) {
    fun search(source: MusicSource, keyword: String, limit: Int = 30): List<Track> =
        when (source) {
            MusicSource.NETEASE -> netease.search(keyword, limit)
            MusicSource.QQ -> qq.search(keyword, limit)
        }

    fun getSongUrl(track: Track, quality: AudioQuality = AudioQuality.STANDARD): SongUrl =
        when (track.source) {
            MusicSource.NETEASE -> netease.getSongUrl(track, quality)
            MusicSource.QQ -> qq.getSongUrl(track, quality)
        }

    fun getLyric(track: Track): Lyric =
        when (track.source) {
            MusicSource.NETEASE -> netease.getLyric(track)
            MusicSource.QQ -> qq.getLyric(track)
        }

    fun createQrCode(source: MusicSource): QrCode =
        when (source) {
            MusicSource.NETEASE -> netease.createQrCode()
            MusicSource.QQ -> qq.createQrCode()
        }

    fun checkQrCode(source: MusicSource, qr: QrCode): QrLoginState =
        when (source) {
            MusicSource.NETEASE -> netease.checkQrCode(qr)
            MusicSource.QQ -> qq.checkQrCode(qr)
        }
}
