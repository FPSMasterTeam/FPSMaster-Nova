package top.fpsmaster.web.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import top.fpsmaster.logger
import top.fpsmaster.web.music.AudioQuality
import top.fpsmaster.web.music.MusicCredentialStore
import top.fpsmaster.web.music.MusicSource
import top.fpsmaster.web.music.NeteaseMusicApi
import top.fpsmaster.web.music.QQMusicApi
import top.fpsmaster.web.music.QrCode
import top.fpsmaster.web.music.Track
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 音乐 API 的本地 HTTP 路由，挂在 LocalServer(:7781) 上。
 *
 * - `/api/netease/` 前缀：**兼容代理**。镜像 NeteaseCloudMusicApi 的端点与响应结构，
 *   前端只需把 BASE_URL 指到这里即可，MusicPlayer.tsx 无需改动。登录态由前端持有，
 *   每次请求带 `?cookie=`，本层用它加密转发；qr/check 成功时回传 Set-Cookie。
 * - `/api/qq/` 前缀：返回规范化 JSON（Track/SongUrl/Lyric），供将来前端接入 QQ 源使用。
 */
class MusicRoutes(
    private val netease: NeteaseMusicApi = NeteaseMusicApi(),
    private val qq: QQMusicApi = QQMusicApi(),
) {
    private val gson = Gson()

    init {
        // 加载持久化的登录凭证并应用到两个客户端
        MusicCredentialStore.load()
        netease.cookie = MusicCredentialStore.neteaseCookie
        qq.musicid = MusicCredentialStore.qqMusicId
        qq.musicKey = MusicCredentialStore.qqMusicKey
    }

    fun register(server: HttpServer) {
        server.createContext("/api/netease/") { ex -> guard(ex) { handleNetease(ex) } }
        server.createContext("/api/qq/") { ex -> guard(ex) { handleQQ(ex) } }
        logger.info("Music API routes registered: /api/netease/*, /api/qq/*")
    }

    // ---- 网易云兼容代理 ----

    private fun handleNetease(ex: HttpExchange) {
        val path = ex.requestURI.path.removePrefix("/api/netease/")
        val q = query(ex)
        // 空 cookie 视为“用 mod 端持久化的凭证”（NeteaseMusicApi.cookie）
        val cookie: String? = q["cookie"]?.takeIf { it.isNotBlank() }

        when (path) {
            "login/qr/key" -> {
                val r = netease.call("/weapi/login/qrcode/unikey", mapOf("type" to "1"), cookie)
                json(ex, """{"data":${r.body},"code":200}""")
            }
            "login/qr/check" -> {
                val key = q["key"] ?: return json(ex, """{"code":400,"message":"missing key"}""")
                val r = netease.call("/weapi/login/qrcode/client/login", mapOf("key" to key, "type" to "1"), cookie)
                val o = gson.fromJson(r.body, JsonObject::class.java)
                val code = o.get("code")?.asInt ?: 400
                val msg = o.get("message")?.takeIf { !it.isJsonNull }?.asString ?: ""
                if (code == 803 && r.cookie.isNotBlank()) { // 登录成功：持久化 cookie
                    netease.cookie = r.cookie
                    MusicCredentialStore.setNetease(r.cookie)
                }
                json(ex, gson.toJson(mapOf("code" to code, "message" to msg, "cookie" to r.cookie)))
            }
            "logout" -> {
                netease.cookie = ""
                MusicCredentialStore.clearNetease()
                json(ex, """{"code":200}""")
            }
            "login/status" ->
                json(ex, """{"data":${netease.call("/weapi/w/nuser/account/get", emptyMap(), cookie).body}}""")
            "user/detail" -> {
                val uid = q["uid"] ?: "0"
                passthrough(ex, netease.call("/weapi/v1/user/detail/$uid", emptyMap(), cookie))
            }
            "user/subcount" -> passthrough(ex, netease.call("/weapi/subcount", emptyMap(), cookie))
            "recommend/songs" -> passthrough(ex, netease.call("/weapi/v3/discovery/recommend/songs", emptyMap(), cookie))
            "recommend/resource" -> passthrough(ex, netease.call("/weapi/v1/discovery/recommend/resource", emptyMap(), cookie))
            "personalized" -> passthrough(ex, netease.call("/weapi/personalized/playlist", mapOf("limit" to (q["limit"] ?: "12")), cookie))
            "dj/recommend" -> passthrough(ex, netease.call("/weapi/djradio/recommend/v1", emptyMap(), cookie))
            "dj/personalize" -> passthrough(ex, netease.call("/weapi/djradio/personalize/rcmd", mapOf("limit" to (q["limit"] ?: "10")), cookie))
            "dj/program" -> passthrough(ex, netease.call("/weapi/dj/program/byradio", mapOf(
                "radioId" to (q["rid"] ?: ""), "limit" to (q["limit"] ?: "100"), "offset" to "0", "asc" to "false",
            ), cookie))
            "user/playlist" -> passthrough(ex, netease.call("/weapi/user/playlist", mapOf(
                "uid" to (q["uid"] ?: "0"),
                "limit" to (q["limit"] ?: "30"),
                "offset" to (q["offset"] ?: "0"),
            ), cookie))
            "playlist/track/all" -> {
                val r = netease.call("/weapi/v6/playlist/detail", mapOf(
                    "id" to (q["id"] ?: ""), "n" to (q["limit"] ?: "1000"), "s" to "8",
                ), cookie)
                val tracks = gson.fromJson(r.body, JsonObject::class.java)
                    ?.getAsJsonObject("playlist")?.getAsJsonArray("tracks")
                json(ex, """{"songs":${tracks ?: "[]"},"code":200}""")
            }
            "song/url/v1" -> passthrough(ex, netease.call("/weapi/song/enhance/player/url/v1", mapOf(
                "ids" to "[${q["id"] ?: ""}]",
                "level" to (q["level"] ?: "standard"),
                "encodeType" to "flac",
            ), cookie))
            "lyric/new", "lyric" -> passthrough(ex, netease.call("/weapi/song/lyric", mapOf(
                "id" to (q["id"] ?: ""), "lv" to "-1", "kv" to "-1", "tv" to "-1",
            ), cookie))
            "cloudsearch", "search" -> passthrough(ex, netease.call("/weapi/cloudsearch/get/web", mapOf(
                "s" to (q["keywords"] ?: q["keyword"] ?: ""),
                "type" to (q["type"] ?: "1"),
                "limit" to (q["limit"] ?: "30"),
                "offset" to (q["offset"] ?: "0"),
            ), cookie))
            else -> notFound(ex)
        }
    }

    // ---- QQ 规范化接口（供将来前端接入）----

    private fun handleQQ(ex: HttpExchange) {
        val path = ex.requestURI.path.removePrefix("/api/qq/")
        val q = query(ex)
        when (path) {
            "search" -> json(ex, gson.toJson(qq.search(q["keyword"] ?: "", (q["limit"] ?: "30").toInt())))
            "toplist" -> json(ex, gson.toJson(qq.getToplist((q["topId"] ?: "26").toInt(), (q["num"] ?: "30").toInt())))
            "recommend" -> json(ex, gson.toJson(qq.getRecommendPlaylists((q["size"] ?: "12").toInt())))
            "playlist" -> json(ex, gson.toJson(qq.getPlaylistTracks(q["id"] ?: "", (q["num"] ?: "100").toInt())))
            "url" -> {
                val track = Track(MusicSource.QQ, id = q["id"] ?: q["mid"] ?: "", mid = q["mid"], name = "", artists = "")
                json(ex, gson.toJson(qq.getSongUrl(track, quality(q["quality"]))))
            }
            "lyric" -> {
                val track = Track(MusicSource.QQ, id = q["id"] ?: q["mid"] ?: "", mid = q["mid"], name = "", artists = "")
                json(ex, gson.toJson(qq.getLyric(track)))
            }
            "qr/create" -> json(ex, gson.toJson(qq.createQrCode()))
            "qr/check" -> {
                val state = qq.checkQrCode(QrCode(q["key"] ?: "", ""))
                if (qq.loggedIn) MusicCredentialStore.setQq(qq.musicid, qq.musicKey) // 持久化
                json(ex, gson.toJson(mapOf("state" to state.name, "loggedIn" to qq.loggedIn, "musicid" to qq.musicid)))
            }
            "login/cookie" -> {
                // 手动 Cookie 登录：用户从浏览器复制 uin(musicid) + qm_keyst(musickey)
                val uin = (q["musicid"] ?: q["uin"] ?: "").trim().removePrefix("o0").trimStart('0')
                val key = (q["musickey"] ?: q["qm_keyst"] ?: "").trim()
                if (uin.isBlank() || key.isBlank()) {
                    json(ex, gson.toJson(mapOf("loggedIn" to false, "error" to "缺少 musicid 或 musickey")))
                } else {
                    qq.musicid = uin
                    qq.musicKey = key
                    MusicCredentialStore.setQq(qq.musicid, qq.musicKey)
                    json(ex, gson.toJson(mapOf("loggedIn" to qq.loggedIn, "musicid" to qq.musicid)))
                }
            }
            "status" -> json(ex, gson.toJson(mapOf(
                "loggedIn" to qq.loggedIn, "musicid" to qq.musicid,
                "user" to if (qq.loggedIn) runCatching { qq.getUserInfo() }.getOrNull() else null,
            )))
            "user" -> json(ex, gson.toJson(qq.getUserInfo()))
            "logout" -> {
                qq.clearLogin()
                MusicCredentialStore.clearQq()
                json(ex, gson.toJson(mapOf("loggedIn" to false)))
            }
            else -> notFound(ex)
        }
    }

    private fun quality(s: String?): AudioQuality = when (s?.lowercase()) {
        "high", "320" -> AudioQuality.HIGH
        "lossless", "flac" -> AudioQuality.LOSSLESS
        else -> AudioQuality.STANDARD
    }

    // ---- 公共工具 ----

    private inline fun guard(ex: HttpExchange, block: () -> Unit) {
        cors(ex)
        if (ex.requestMethod.equals("OPTIONS", true)) { // 预检
            ex.sendResponseHeaders(204, -1); ex.close(); return
        }
        try {
            block()
        } catch (e: Exception) {
            logger.error("Music route error: ${ex.requestURI}", e)
            json(ex, gson.toJson(mapOf("code" to 500, "error" to (e.message ?: "internal error"))), status = 200)
        }
    }

    private fun passthrough(ex: HttpExchange, r: NeteaseMusicApi.RawResult) = json(ex, r.body)

    private fun query(ex: HttpExchange): Map<String, String> {
        val raw = ex.requestURI.rawQuery ?: return emptyMap()
        return raw.split('&').mapNotNull {
            val i = it.indexOf('='); if (i < 0) return@mapNotNull null
            val k = URLDecoder.decode(it.substring(0, i), StandardCharsets.UTF_8)
            val v = URLDecoder.decode(it.substring(i + 1), StandardCharsets.UTF_8)
            k to v
        }.toMap()
    }

    private fun cors(ex: HttpExchange) {
        ex.responseHeaders.apply {
            add("Access-Control-Allow-Origin", "*")
            add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            add("Access-Control-Allow-Headers", "Content-Type")
        }
    }

    private fun json(ex: HttpExchange, body: String, status: Int = 200) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun notFound(ex: HttpExchange) = json(ex, """{"code":404,"error":"unknown music endpoint"}""", 200)
}
