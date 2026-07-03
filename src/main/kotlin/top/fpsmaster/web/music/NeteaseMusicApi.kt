package top.fpsmaster.web.music

import com.google.gson.Gson
import com.google.gson.JsonObject
import top.fpsmaster.web.music.crypto.NeteaseCrypto
import top.fpsmaster.web.music.http.MusicHttp

/**
 * 网易云音乐客户端。走 web 端 "weapi" 加密接口，功能完整且稳定。
 *
 * 覆盖：搜索、播放直链、歌词、二维码登录、登录状态、每日推荐、用户歌单、歌单详情。
 *
 * 登录态：二维码登录成功后 [cookie] 会被写入 `MUSIC_U`，之后的请求自动带上。
 * 高音质 / VIP 歌曲需要对应黑胶会员的登录 cookie，否则接口会回退或返回空链接。
 *
 * 线程安全性：单实例非并发安全，若并发使用请每线程一个实例或自行加锁。
 */
class NeteaseMusicApi(
    private val http: MusicHttp = MusicHttp(),
    initialCookie: String = "",
) {
    private val gson = Gson()

    /** 完整 cookie 串（如登录后持久化到磁盘再回填）。 */
    var cookie: String = initialCookie

    // ---- 公开 API ----

    /** 搜索歌曲。 */
    fun search(keyword: String, limit: Int = 30, offset: Int = 0): List<Track> {
        val data = weapi(
            "/weapi/cloudsearch/get/web",
            mapOf("s" to keyword, "type" to "1", "limit" to limit.toString(), "offset" to offset.toString(), "total" to "true"),
        )
        val songs = data.getAsJsonObject("result")?.getAsJsonArray("songs") ?: return emptyList()
        return songs.mapNotNull { runCatching { parseTrack(it.asJsonObject) }.getOrNull() }
    }

    /** 获取播放直链。 */
    fun getSongUrl(track: Track, quality: AudioQuality = AudioQuality.STANDARD): SongUrl {
        val level = when (quality) {
            AudioQuality.STANDARD -> "standard"
            AudioQuality.HIGH -> "exhigh"
            AudioQuality.LOSSLESS -> "lossless"
        }
        val data = weapi(
            "/weapi/song/enhance/player/url/v1",
            mapOf("ids" to "[${track.id}]", "level" to level, "encodeType" to "flac"),
        )
        val item = data.getAsJsonArray("data")?.firstOrNull()?.asJsonObject
            ?: return SongUrl(null, quality)
        val url = item.get("url")?.takeIf { !it.isJsonNull }?.asString
        val type = item.get("type")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val size = item.get("size")?.takeIf { !it.isJsonNull }?.asLong ?: 0
        // freeTrialInfo 非空 => 该链接是试听片段（start/end 单位为秒）
        val trial = item.getAsJsonObject("freeTrialInfo")?.takeIf { !it.isJsonNull }
        return if (trial != null) {
            val start = trial.get("start")?.takeIf { !it.isJsonNull }?.asLong ?: 0
            val end = trial.get("end")?.takeIf { !it.isJsonNull }?.asLong ?: 0
            SongUrl(url, quality, type, size, isTrial = true, trialStartMs = start * 1000, trialEndMs = end * 1000)
        } else {
            SongUrl(url, quality, type, size)
        }
    }

    /** 获取歌词（含翻译）。 */
    fun getLyric(track: Track): Lyric {
        val data = weapi(
            "/weapi/song/lyric",
            mapOf("id" to track.id, "lv" to "-1", "kv" to "-1", "tv" to "-1"),
        )
        val lrc = data.getAsJsonObject("lrc")?.get("lyric")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val tlyric = data.getAsJsonObject("tlyric")?.get("lyric")?.takeIf { !it.isJsonNull }?.asString
        return Lyric(lrc, tlyric?.ifBlank { null })
    }

    // ---- 二维码登录 ----

    /** 第一步：创建二维码。前端用 [QrCode.qrContent] 渲染二维码图片。 */
    fun createQrCode(): QrCode {
        val keyData = weapi("/weapi/login/qrcode/unikey", mapOf("type" to "1"))
        val unikey = keyData.get("unikey")?.asString
            ?: throw MusicApiException("网易云：获取 unikey 失败")
        return QrCode(unikey, "https://music.163.com/login?codekey=$unikey")
    }

    /**
     * 第二步：轮询二维码状态。返回 [QrLoginState.CONFIRMED] 时，登录 cookie 已写入 [cookie]。
     * 建议每 2~3 秒轮询一次。
     */
    fun checkQrCode(qr: QrCode): QrLoginState {
        val resp = http.postForm(
            "https://music.163.com/weapi/login/qrcode/client/login",
            weapiForm(mapOf("key" to qr.key, "type" to "1")),
            headers = baseHeaders(),
            cookies = buildCookies(cookie),
        )
        val json = gson.fromJson(resp.body, JsonObject::class.java)
        return when (json.get("code")?.asInt) {
            803 -> {
                captureCookies(resp.setCookies)
                QrLoginState.CONFIRMED
            }
            802 -> QrLoginState.SCANNED
            801 -> QrLoginState.WAITING
            800 -> QrLoginState.EXPIRED
            else -> QrLoginState.ERROR
        }
    }

    /** 是否已登录，返回用户 id（未登录为 null）。 */
    fun getLoginUid(): Long? {
        val data = weapi("/weapi/w/nuser/account/get", emptyMap())
        return data.getAsJsonObject("account")?.get("id")?.takeIf { !it.isJsonNull }?.asLong
    }

    /** 每日推荐歌曲（需登录）。 */
    fun getDailyRecommendSongs(): List<Track> {
        val data = weapi("/weapi/v3/discovery/recommend/songs", emptyMap())
        val songs = data.getAsJsonObject("data")?.getAsJsonArray("dailySongs") ?: return emptyList()
        return songs.mapNotNull { runCatching { parseTrack(it.asJsonObject) }.getOrNull() }
    }

    /** 用户歌单列表（需登录）。 */
    fun getUserPlaylists(uid: Long, limit: Int = 30, offset: Int = 0): List<PlaylistBrief> {
        val data = weapi(
            "/weapi/user/playlist",
            mapOf("uid" to uid.toString(), "limit" to limit.toString(), "offset" to offset.toString(), "includeVideo" to "true"),
        )
        val list = data.getAsJsonArray("playlist") ?: return emptyList()
        return list.mapNotNull {
            val o = it.asJsonObject
            PlaylistBrief(
                id = o.get("id")?.asString ?: return@mapNotNull null,
                name = o.get("name")?.asString ?: "",
                coverUrl = o.get("coverImgUrl")?.takeIf { c -> !c.isJsonNull }?.asString,
                trackCount = o.get("trackCount")?.asInt ?: 0,
            )
        }
    }

    /** 歌单内歌曲（一次最多 ~1000 首）。 */
    fun getPlaylistTracks(playlistId: String, limit: Int = 1000): List<Track> {
        val data = weapi(
            "/weapi/v6/playlist/detail",
            mapOf("id" to playlistId, "n" to limit.toString(), "s" to "8"),
        )
        val tracks = data.getAsJsonObject("playlist")?.getAsJsonArray("tracks") ?: return emptyList()
        return tracks.mapNotNull { runCatching { parseTrack(it.asJsonObject) }.getOrNull() }
    }

    fun clearLogin() {
        cookie = ""
    }

    // ---- 内部 ----

    private fun parseTrack(o: JsonObject): Track {
        val artists = (o.getAsJsonArray("ar") ?: o.getAsJsonArray("artists"))
            ?.mapNotNull { it.asJsonObject.get("name")?.asString }
            ?.joinToString(" / ") ?: ""
        val albumObj = o.getAsJsonObject("al") ?: o.getAsJsonObject("album")
        // fee: 0=免费, 1=VIP专享, 4=数字专辑(需购买), 8=免费低音质/高音质需VIP。
        // 除 0 外都需要相应权限才能完整播放。
        val fee = o.get("fee")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        return Track(
            source = MusicSource.NETEASE,
            id = o.get("id").asString,
            name = o.get("name")?.asString ?: "",
            artists = artists,
            album = albumObj?.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
            durationMs = (o.get("dt") ?: o.get("duration"))?.takeIf { !it.isJsonNull }?.asLong ?: 0,
            coverUrl = albumObj?.get("picUrl")?.takeIf { !it.isJsonNull }?.asString,
            vip = fee != 0,
        )
    }

    /** 原始 weapi 调用结果（供本地 HTTP 代理层透传给前端）。 */
    data class RawResult(val body: String, val cookie: String)

    /**
     * 用指定 cookie 发一次 weapi 请求，返回**原始响应体**与本次 Set-Cookie。
     * 供 LocalServer 的兼容代理层使用：前端按 NeteaseCloudMusicApi 的结构解析，
     * 这里只负责加密与转发，不做规范化。
     *
     * @param path            weapi 路径，如 `/weapi/song/enhance/player/url/v1`。
     * @param params          业务参数（会被 JSON 化后加密）。
     * @param cookieOverride  本次请求使用的完整 cookie 串（前端持有登录态时传入）。
     */
    fun call(path: String, params: Map<String, String>, cookieOverride: String? = null): RawResult {
        val resp = postWeapi(path, params, buildCookies(cookieOverride ?: cookie))
        return RawResult(resp.body, resp.setCookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
    }

    /** 发一次 weapi 请求并返回响应 JSON。 */
    private fun weapi(path: String, params: Map<String, String>): JsonObject {
        val resp = postWeapi(path, params, buildCookies(cookie))
        return gson.fromJson(resp.body, JsonObject::class.java)
            ?: throw MusicApiException("网易云：响应解析失败 ($path)")
    }

    private fun postWeapi(path: String, params: Map<String, String>, cookies: Map<String, String>): MusicHttp.Response =
        http.postForm("https://music.163.com$path", weapiForm(params), headers = baseHeaders(), cookies = cookies)

    private fun weapiForm(params: Map<String, String>): Map<String, String> {
        val json = gson.toJson(params)
        val enc = NeteaseCrypto.weapi(json)
        return mapOf("params" to enc.params, "encSecKey" to enc.encSecKey)
    }

    private fun baseHeaders() = mapOf(
        "Referer" to "https://music.163.com",
        "Origin" to "https://music.163.com",
    )

    private fun buildCookies(cookieString: String): Map<String, String> {
        val base = linkedMapOf("os" to "pc", "appver" to "8.9.70")
        cookieString.split(';').forEach { part ->
            val name = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            if (name.isNotEmpty()) base[name] = value
        }
        return base
    }

    private fun captureCookies(set: Map<String, String>) {
        // 至少需要 MUSIC_U；把新 cookie 合并进现有串
        val merged = LinkedHashMap<String, String>()
        cookie.split(';').forEach {
            val n = it.substringBefore('=').trim(); val v = it.substringAfter('=', "").trim()
            if (n.isNotEmpty()) merged[n] = v
        }
        set.forEach { (k, v) -> merged[k] = v }
        cookie = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}

/** 歌单摘要。 */
data class PlaylistBrief(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
)
