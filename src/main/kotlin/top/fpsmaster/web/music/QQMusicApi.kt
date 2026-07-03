package top.fpsmaster.web.music

import com.google.gson.Gson
import com.google.gson.JsonObject
import top.fpsmaster.web.music.crypto.QQHash
import top.fpsmaster.web.music.crypto.QQSign
import top.fpsmaster.web.music.http.MusicHttp
import java.util.Base64
import java.util.UUID

/**
 * QQ 音乐客户端。走 web 端 `u.y.qq.com/cgi-bin/musicu.fcg`(不签名) / `musics.fcg`(zzc 签名)。
 *
 * 覆盖：搜索、歌词、播放直链(vkey)、QQ 扫码登录(ptlogin 流程)。
 *
 * 登录态：扫码登录成功后 [musicid] / [musicKey] 会被写入，之后请求自动带上。
 * 高音质(FLAC/320) 与 VIP 歌曲需要绿钻会员登录，否则 purl 为空 → 链接不可用。
 *
 * 说明：QQ 的扫码登录（ptlogin）参数会随腾讯改版漂移，上线前务必真机联调；
 * 若某天登录失效，优先核对 [createQrCode]/[checkQrCode] 里的 aid/daid/pt_3rd_aid 等常量。
 */
class QQMusicApi(
    private val http: MusicHttp = MusicHttp(),
) {
    private val gson = Gson()
    // 数字 guid（对齐 Meting：Math.random()*1e10），QQ vkey 会按此绑定凭证
    private val guid: String = (1_000_000_000L + kotlin.random.Random.nextLong(9_000_000_000L)).toString()

    var musicid: String = ""
    var musicKey: String = ""

    val loggedIn: Boolean get() = musicid.isNotEmpty() && musicKey.isNotEmpty()

    // 整个登录流程共享的 cookie jar（模拟浏览器 Session，跨 ptqrshow/ptqrlogin/
    // check_sig/authorize 累积，合并时跳过空值——这是拿到 code 的关键）。
    private val loginJar = LinkedHashMap<String, String>()

    // ---- 公开 API ----

    /** 搜索歌曲。走 musicu DoSearchForQQMusicDesktop（对齐 Yueby，解析 body.song.list）。 */
    fun search(keyword: String, limit: Int = 30, page: Int = 1): List<Track> {
        val data = musicu(
            module = "music.search.SearchCgiService",
            method = "DoSearchForQQMusicDesktop",
            param = mapOf(
                "num_per_page" to limit, "page_num" to page,
                "search_type" to 0, "query" to keyword, "grp" to 1,
            ),
            reqKey = "music.search.SearchCgiService.DoSearchForQQMusicDesktop",
            overrideComm = mapOf("ct" to "6", "cv" to "80600", "tmeAppID" to "qqmusic"),
        )
        val list = data.getAsJsonObject("body")?.getAsJsonObject("song")?.getAsJsonArray("list") ?: return emptyList()
        top.fpsmaster.logger.info("QQ search '$keyword': got ${list.size()} 首")
        return list.mapNotNull { runCatching { parseTrack(it.asJsonObject) }.getOrNull() }
    }

    /** 推荐歌单（发现页）。 */
    fun getRecommendPlaylists(size: Int = 12): List<MusicPlaylist> {
        val data = musicu(
            module = "music.playlist.PlaylistSquare", method = "GetRecommendFeed",
            param = mapOf("From" to 0, "Size" to size),
            reqKey = "req", overrideComm = mapOf("ct" to "24", "cv" to "0"),
        )
        val list = data.getAsJsonArray("List") ?: return emptyList()
        return list.mapNotNull { el ->
            val basic = el.asJsonObject.getAsJsonObject("Playlist")?.getAsJsonObject("basic") ?: return@mapNotNull null
            val cover = basic.getAsJsonObject("cover")
            MusicPlaylist(
                source = MusicSource.QQ,
                id = basic.get("tid")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null,
                name = basic.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
                coverUrl = (cover?.get("medium_url") ?: cover?.get("small_url"))?.takeIf { !it.isJsonNull }?.asString,
                trackCount = basic.get("song_cnt")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            )
        }
    }

    /** 歌单内歌曲。 */
    fun getPlaylistTracks(dissid: String, num: Int = 100): List<Track> {
        val data = musicu(
            module = "music.srfDissInfo.DissInfo", method = "CgiGetDiss",
            param = mapOf("disstid" to (dissid.toLongOrNull() ?: 0L), "onlysong" to 0, "song_begin" to 0, "song_num" to num),
            reqKey = "req", overrideComm = mapOf("ct" to "24", "cv" to "0"),
        )
        val list = data.getAsJsonArray("songlist") ?: return emptyList()
        return list.mapNotNull { runCatching { parseTrack(it.asJsonObject) }.getOrNull() }
    }

    /**
     * 获取排行榜歌曲（发现页默认列表）。topId 默认 26=巅峰榜·热歌。
     * GetDetail 的 songInfoList 是完整 Song 对象，可直接播放。
     */
    fun getToplist(topId: Int = 26, num: Int = 30): List<Track> {
        val data = musicu(
            module = "music.musicToplist.Toplist",
            method = "GetDetail",
            param = mapOf("topId" to topId, "offset" to 0, "num" to num),
        )
        val list = data.getAsJsonArray("songInfoList") ?: return emptyList()
        top.fpsmaster.logger.info("QQ toplist $topId: ${list.size()} 首")
        return list.mapNotNull { runCatching { parseTrack(it.asJsonObject) }.getOrNull() }
    }

    /**
     * 获取播放直链(vkey)。
     *
     * @return [SongUrl.url] 不可用（VIP/无版权/需登录）时为 null。
     */
    fun getSongUrl(track: Track, quality: AudioQuality = AudioQuality.STANDARD): SongUrl {
        val mid = track.mid ?: throw MusicApiException("QQ：缺少 songmid")

        // 1. 查歌曲详情，拿 media_mid（filename 必须用它，不是 songmid）+ 各音质可用性 + type
        val detail = http.get(
            "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg",
            query = mapOf("songmid" to mid, "platform" to "yqq", "format" to "json"),
            headers = mapOf("Referer" to "https://y.qq.com/"),
            cookies = credentialCookies(),
        )
        val djson = runCatching { gson.fromJson(detail.body, JsonObject::class.java) }.getOrNull()
        val song = djson?.getAsJsonArray("data")?.firstOrNull()?.asJsonObject
            ?: return SongUrl(null, quality, reason = "获取歌曲信息失败")
        val file = song.getAsJsonObject("file")
        val mediaMid = file?.get("media_mid")?.takeIf { !it.isJsonNull }?.asString ?: mid
        val songType = song.get("type")?.takeIf { !it.isJsonNull }?.asInt ?: 0

        // 2. 按请求档位由高到低构造候选音质（只保留该歌确实有的文件），对齐 Meting
        data class QCand(val sizeKey: String, val prefix: String, val ext: String, val q: AudioQuality)
        val all = listOf(
            QCand("size_flac", "F000", "flac", AudioQuality.LOSSLESS),
            QCand("size_320mp3", "M800", "mp3", AudioQuality.HIGH),
            QCand("size_128mp3", "M500", "mp3", AudioQuality.STANDARD),
            QCand("size_96aac", "C400", "m4a", AudioQuality.STANDARD),
        )
        val start = when (quality) { AudioQuality.LOSSLESS -> 0; AudioQuality.HIGH -> 1; AudioQuality.STANDARD -> 2 }
        val cands = all.subList(start, all.size)
            .filter { file != null && (file.get(it.sizeKey)?.takeIf { s -> !s.isJsonNull }?.asLong ?: 0L) > 0L }
        if (cands.isEmpty()) return SongUrl(null, quality, reason = "无可用音频")

        // 3. GetVkeyServer/CgiGetVkey：filename = prefix + media_mid + ext（一次请求所有候选音质）
        //    对齐 Meting：GET musicu.fcg?data=<payload>，payload 无 comm、键为 req_0、不签名
        val data = vkeyRequest(mapOf(
            "guid" to guid,
            "songmid" to cands.map { mid },
            "filename" to cands.map { "${it.prefix}$mediaMid.${it.ext}" },
            "songtype" to cands.map { songType },
            "uin" to musicid.ifEmpty { "0" },
            "loginflag" to 1,
            "platform" to "20",
        ))
        val infos = data.getAsJsonArray("midurlinfo")
        val sip = (data.getAsJsonArray("sip")?.firstOrNull()?.asString ?: "https://dl.stream.qqmusic.qq.com/")
            .replaceFirst("http://", "https://") // CEF 里避免混合内容被拦
        for (i in cands.indices) {
            val purl = infos?.get(i)?.asJsonObject?.get("purl")?.takeIf { !it.isJsonNull }?.asString
            if (!purl.isNullOrEmpty()) {
                top.fpsmaster.logger.info("QQ vkey $mid: ${cands[i].prefix} ok")
                return SongUrl(sip + purl, cands[i].q, cands[i].ext)
            }
        }
        // 无 purl：按 result 码 / 标志位解析原因
        val info0 = infos?.firstOrNull()?.asJsonObject
        val result = info0?.get("result")?.takeIf { !it.isJsonNull }?.asInt ?: -1
        val needBuy = info0?.get("pneedbuy")?.takeIf { !it.isJsonNull }?.asInt == 1
        val reason = when {
            result == 104003 -> "需要绿钻会员（或无版权）"
            needBuy || track.vip -> "需要 QQ 音乐会员/付费"
            else -> "暂时无法播放"
        }
        top.fpsmaster.logger.info("QQ vkey $mid: 无 purl, result=$result → $reason")
        return SongUrl(null, quality, reason = reason)
    }

    /**
     * 获取歌词（含翻译）。走旧版 GET 端点，返回明文 LRC，避开 QRC 三重 DES 解密。
     */
    fun getLyric(track: Track): Lyric {
        val mid = track.mid ?: throw MusicApiException("QQ：缺少 songmid")
        val resp = http.get(
            "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
            query = mapOf("songmid" to mid, "format" to "json", "nobase64" to "1", "g_tk" to "5381"),
            headers = mapOf("Referer" to "https://y.qq.com/"),
        )
        // 只有 JSONP 包裹(MusicJsonCallback(...))才剥括号；纯 JSON 直接解析
        // —— 否则歌词内容里的括号(如"周杰伦 (Jay Chou)")会被 substringAfter('(') 误切断
        val jsonText = resp.body.trim().let {
            if (it.startsWith("{")) it else it.substringAfter('(').substringBeforeLast(')')
        }
        val json = runCatching { gson.fromJson(jsonText, JsonObject::class.java) }.getOrNull()
            ?: return Lyric("")
        fun decode(field: String): String? {
            val raw = json.get(field)?.takeIf { !it.isJsonNull }?.asString ?: return null
            if (raw.isBlank()) return null
            // nobase64=1 通常已是明文；个别情况仍是 base64，做一次兜底解码
            return runCatching { String(Base64.getDecoder().decode(raw), Charsets.UTF_8) }
                .getOrNull()?.takeIf { it.contains('[') } ?: raw
        }
        return Lyric(decode("lyric") ?: "", decode("trans"))
    }

    /** 登录用户信息（昵称/头像）。未登录返回 null。 */
    fun getUserInfo(): QQUserInfo? {
        if (!loggedIn) return null
        val resp = http.get(
            "https://c6.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg",
            query = mapOf("ct" to "20", "cv" to "4747474", "cid" to "205360838", "userid" to musicid),
            headers = mapOf("Referer" to "https://y.qq.com/"),
            cookies = credentialCookies(),
        )
        val text = resp.body.let { if (it.startsWith("callback(") || it.contains(")(")) it.substringAfter('(').substringBeforeLast(')') else it }
        val json = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
        val creator = json?.getAsJsonObject("data")?.getAsJsonObject("creator")
        var nick = creator?.get("nick")?.takeIf { !it.isJsonNull }?.asString ?: "QQ用户$musicid"
        // 昵称可能是 base64（多为中文名）。仅当解码结果是干净文本时才采用，
        // 否则像 "SuperSkidder" 这类普通名会被误解码成乱码。
        if (nick.matches(Regex("^[A-Za-z0-9+/]+={0,2}$")) && nick.length % 4 == 0) {
            runCatching { String(Base64.getDecoder().decode(nick), Charsets.UTF_8) }.getOrNull()
                ?.takeIf { s -> s.isNotBlank() && s.none { it.code < 0x20 || it.code == 0xFFFD } }
                ?.let { nick = it }
        }
        // 头像用响应里的 headpic（真实头像），没有再回退到 qlogo
        val headpic = creator?.get("headpic")?.takeIf { !it.isJsonNull }?.asString
        val avatar = headpic?.takeIf { it.isNotBlank() } ?: "https://q1.qlogo.cn/g?b=qq&nk=$musicid&s=100"
        return QQUserInfo(musicid, nick, avatar)
    }

    // ---- QQ 扫码登录（ptlogin 流程）----

    /**
     * 第一步：获取 QQ 登录二维码。
     *
     * [QrCode.qrContent] 是 `data:image/png;base64,...` 形式的图片，前端直接当 img src 显示；
     * [QrCode.key] 是 qrsig，用于 [checkQrCode] 轮询。
     */
    fun createQrCode(): QrCode {
        loginJar.clear()
        // ptqrshow：拿二维码 + qrsig（对齐 Yueby 的最小流程，不做 initparams/xlogin）
        val show = jarGet("https://ssl.ptlogin2.qq.com/ptqrshow", mapOf(
            "appid" to "716027609", "e" to "2", "l" to "M", "s" to "3", "d" to "72",
            "v" to "4", "t" to "0.$loginNonce", "daid" to "383", "pt_3rd_aid" to "100497308",
        ), "https://xui.ptlogin2.qq.com/")
        val qrsig = loginJar["qrsig"] ?: throw MusicApiException("QQ：获取 qrsig 失败")
        val dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(show.bytes)
        return QrCode(qrsig, dataUrl)
    }

    /**
     * 第二步：轮询二维码状态。返回 [QrLoginState.CONFIRMED] 时已完成登录，
     * [musicid]/[musicKey] 已写入。建议每 2~3 秒轮询一次。
     */
    fun checkQrCode(qr: QrCode): QrLoginState {
        val resp = jarGet("https://ssl.ptlogin2.qq.com/ptqrlogin", mapOf(
            "u1" to "https://graph.qq.com/oauth2.0/login_jump",
            "ptqrtoken" to QQHash.hash33(qr.key).toString(),
            "ptredirect" to "0", "h" to "1", "t" to "1", "g" to "1", "from_ui" to "1",
            "ptlang" to "2052", "action" to "0-0-$loginNonce", "js_ver" to "20102616",
            "js_type" to "1", "login_sig" to "", "pt_uistyle" to "40", "aid" to "716027609",
            "daid" to "383", "pt_3rd_aid" to "100497308", "has_onekey" to "1",
        ), "https://xui.ptlogin2.qq.com/")
        val args = PTUI_CB.find(resp.body)?.groupValues?.get(1)
            ?.let { PTUI_ARG.findAll(it).map { m -> m.groupValues[1] }.toList() }
            ?: return QrLoginState.ERROR

        return when (args.getOrNull(0)) {
            "0" -> {
                // 从 args[2] 的跳转 URL 里提取 uin + ptsigx，自己拼 check_sig（对齐 Yueby）
                val url = args.getOrNull(2) ?: return QrLoginState.ERROR
                val sigx = SIGX_RE.find(url)?.groupValues?.get(1) ?: return QrLoginState.ERROR
                val uin = UIN_RE.find(url)?.groupValues?.get(1) ?: return QrLoginState.ERROR
                try {
                    authorizeQq(uin, sigx)
                    QrLoginState.CONFIRMED
                } catch (e: Exception) {
                    top.fpsmaster.logger.error("QQ 授权失败", e)
                    QrLoginState.ERROR
                }
            }
            "67" -> QrLoginState.SCANNED
            "66" -> QrLoginState.WAITING
            "65" -> QrLoginState.EXPIRED
            else -> QrLoginState.ERROR // 68=拒绝等
        }
    }

    fun clearLogin() {
        musicid = ""; musicKey = ""; loginJar.clear()
    }

    // ---- 登录收尾：check_sig → login_jump → authorize → code → qm_keyst ----

    private fun authorizeQq(uin: String, sigx: String) {
        // 4. check_sig：自己用 Yueby 的参数拼 URL（ptredirect=100 让它直接回 p_skey 而非跳转），单次 GET，跳空值合并
        jarGet("https://ssl.ptlogin2.graph.qq.com/check_sig", mapOf(
            "uin" to uin, "pttype" to "1", "service" to "ptqrlogin", "nodirect" to "0",
            "ptsigx" to sigx, "s_url" to "https://graph.qq.com/oauth2.0/login_jump",
            "ptlang" to "2052", "ptredirect" to "100", "aid" to "716027609", "daid" to "383",
            "j_later" to "0", "low_login_hour" to "0", "regmaster" to "0",
            "pt_login_type" to "3", "pt_aid" to "0", "pt_aaid" to "16", "pt_light" to "0",
            "pt_3rd_aid" to "100497308",
        ), "https://xui.ptlogin2.qq.com/").also {
            top.fpsmaster.logger.info("QQ check_sig: status=${it.status}, setCookies=${it.setCookies.keys}, jar=${loginJar.keys}")
        }
        val pSkey = loginJar["p_skey"] ?: throw MusicApiException("QQ：获取 p_skey 失败")
        top.fpsmaster.logger.info("QQ authorize: jar=${loginJar.keys}")

        // 5. authorize POST → 302 到带 code 的回调（参数对齐 Yueby/music-together 当前可用实现）
        val authorize = jarPostForm("https://graph.qq.com/oauth2.0/authorize", mapOf(
            "response_type" to "code", "client_id" to "100497308",
            "redirect_uri" to "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/",
            "scope" to "get_user_info,get_app_friends", "state" to "state", "switch" to "", "from_ptlogin" to "1",
            "src" to "1", "update_auth" to "1", "openapi" to "1010_1030",
            "g_tk" to QQHash.hash33(pSkey, 5381).toString(),
            "auth_time" to loginNonce, "ui" to UUID.randomUUID().toString(),
        ), "https://graph.qq.com/")
        val code = followForCode(authorize)

        // QQLogin：code → musicid + musickey
        val data = musicu(
            module = "QQConnectLogin.LoginServer",
            method = "QQLogin",
            param = mapOf("code" to code),
            extraComm = mapOf("tmeLoginType" to 2),
        )
        musicid = data.get("musicid")?.takeIf { !it.isJsonNull }?.asString ?: ""
        musicKey = data.get("musickey")?.takeIf { !it.isJsonNull }?.asString ?: ""
        if (!loggedIn) throw MusicApiException("QQ：登录换取凭证失败")
    }

    /**
     * 合并 Set-Cookie 进 jar，**跳过空值**（服务器发的过期清除操作）。
     * 这是登录成功的关键——否则后续请求的清除会把好好的会话 cookie 覆盖成空，
     * 导致 graph.qq.com 认为未登录、弹出 show?which=Login。
     */
    private fun mergeIntoJar(setCookies: Map<String, String>) {
        setCookies.forEach { (k, v) -> if (v.isNotEmpty()) loginJar[k] = v }
    }

    /** GET 并把响应 Set-Cookie 累积回共享 jar（模拟浏览器 Session，跳过空值）。 */
    private fun jarGet(url: String, query: Map<String, String>, referer: String): MusicHttp.Response {
        val r = http.get(url, query, headers = mapOf("Referer" to referer), cookies = loginJar)
        mergeIntoJar(r.setCookies)
        return r
    }

    private fun jarPostForm(url: String, form: Map<String, String>, referer: String): MusicHttp.Response {
        val r = http.postForm(url, form, headers = mapOf("Referer" to referer), cookies = loginJar)
        mergeIntoJar(r.setCookies)
        return r
    }

    /** 从 authorize 响应跟随重定向链，直到 Location/body 出现授权 code。 */
    private fun followForCode(start: MusicHttp.Response): String {
        var status = start.status
        var loc: String? = start.header("Location")
        var body = start.body
        top.fpsmaster.logger.info("QQ authorize resp: status=${start.status}, firstLoc='${(loc ?: "").take(220)}'")
        CODE_RE.find(loc ?: "")?.let { return it.groupValues[1] }

        var hops = 0
        while (loc != null && hops < 6) {
            val url = if (loc.startsWith("http")) loc else "https://graph.qq.com$loc"
            val r = jarGet(url, emptyMap(), "https://graph.qq.com/")
            status = r.status
            body = r.body
            loc = r.header("Location")
            CODE_RE.find(loc ?: "")?.let { return it.groupValues[1] }
            CODE_RE.find(body)?.let { return it.groupValues[1] }
            hops++
        }
        val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1) ?: ""
        val bodyClean = body.replace(Regex("\\s+"), " ").take(400)
        throw MusicApiException(
            "QQ：获取授权 code 失败 (status=$status, title='$title', lastLoc='${(loc ?: "").take(160)}', body='$bodyClean')"
        )
    }

    // ---- 内部：musicu / musics 请求 ----

    private fun musicu(
        module: String,
        method: String,
        param: Map<String, Any>,
        sign: Boolean = false,
        extraComm: Map<String, Any> = emptyMap(),
        reqKey: String = "req_1",
        overrideComm: Map<String, Any>? = null,
    ): JsonObject {
        val comm = overrideComm ?: buildComm(extraComm)
        val payload = JsonObject().apply {
            add("comm", gson.toJsonTree(comm))
            add(reqKey, gson.toJsonTree(mapOf("module" to module, "method" to method, "param" to param)))
        }
        val body = gson.toJson(payload)
        val query = if (sign) mapOf("_" to loginNonce, "sign" to QQSign.sign(body)) else emptyMap()
        val url = if (sign) "https://u.y.qq.com/cgi-bin/musics.fcg" else "https://u.y.qq.com/cgi-bin/musicu.fcg"

        val resp = http.postJson(
            url, body, query,
            headers = mapOf("Referer" to "https://y.qq.com/"),
            cookies = credentialCookies(),
        )
        val json = gson.fromJson(resp.body, JsonObject::class.java)
            ?: throw MusicApiException("QQ：响应解析失败 ($module.$method)")
        val req = json.getAsJsonObject(reqKey) ?: throw MusicApiException("QQ：响应缺少 $reqKey")
        val code = req.get("code")?.asInt ?: -1
        if (code != 0) throw MusicApiException("QQ：接口返回 code=$code ($module.$method)")
        return req.getAsJsonObject("data") ?: JsonObject()
    }

    /** vkey 请求（对齐 Meting）：GET musicu.fcg?data=<payload>，payload 仅 req_0、无 comm、不签名。 */
    private fun vkeyRequest(param: Map<String, Any>): JsonObject {
        top.fpsmaster.logger.info("QQ vkey req: cookies=${credentialCookies().keys}, keystPrefix='${musicKey.take(6)}' guid=$guid")
        val payload = JsonObject().apply {
            add("req_0", gson.toJsonTree(mapOf("module" to "vkey.GetVkeyServer", "method" to "CgiGetVkey", "param" to param)))
        }
        val resp = http.get(
            "https://u.y.qq.com/cgi-bin/musicu.fcg",
            query = mapOf("format" to "json", "platform" to "yqq.json", "needNewCode" to "0", "data" to gson.toJson(payload)),
            headers = mapOf("Referer" to "https://y.qq.com/"),
            cookies = credentialCookies(),
        )
        val json = gson.fromJson(resp.body, JsonObject::class.java) ?: return JsonObject()
        return json.getAsJsonObject("req_0")?.getAsJsonObject("data") ?: JsonObject()
    }

    private fun buildComm(extra: Map<String, Any>): Map<String, Any> {
        val comm = linkedMapOf<String, Any>(
            "ct" to "11", "cv" to "12080008", "v" to "12080008",
            "tmeAppID" to "qqmusic", "format" to "json",
            "inCharset" to "utf-8", "outCharset" to "utf-8",
            "uin" to musicid.ifEmpty { "0" },
        )
        if (loggedIn) {
            comm["qq"] = musicid
            comm["authst"] = musicKey
        }
        comm.putAll(extra)
        return comm
    }

    private fun credentialCookies(): Map<String, String> {
        if (!loggedIn) return emptyMap()
        return mapOf(
            "uin" to musicid, "qqmusic_uin" to musicid,
            "qm_keyst" to musicKey, "qqmusic_key" to musicKey,
        )
    }

    private fun parseTrack(o: JsonObject): Track {
        val mid = (o.get("mid") ?: o.get("songmid"))?.takeIf { !it.isJsonNull }?.asString
        val id = (o.get("id") ?: o.get("songid"))?.takeIf { !it.isJsonNull }?.asString ?: (mid ?: "")
        val singers = o.getAsJsonArray("singer")
            ?.mapNotNull { it.asJsonObject.get("name")?.asString }
            ?.joinToString(" / ") ?: ""
        val album = o.getAsJsonObject("album")
        val albumName = album?.get("name")?.takeIf { !it.isJsonNull }?.asString
            ?: o.get("albumname")?.takeIf { !it.isJsonNull }?.asString ?: ""
        // 封面优先用 album.pmid（专辑图专用 mid），无则用 album.mid
        val albumPmid = album?.get("pmid")?.takeIf { !it.isJsonNull }?.asString
            ?: album?.get("mid")?.takeIf { !it.isJsonNull }?.asString
            ?: o.get("albummid")?.takeIf { !it.isJsonNull }?.asString
        val interval = o.get("interval")?.takeIf { !it.isJsonNull }?.asLong ?: 0
        // 只有 pay_play==1 才是"付费才能播"（VIP）；pay_month/pay_down 免费歌也可能为 1
        val vip = o.getAsJsonObject("pay")?.get("pay_play")?.takeIf { !it.isJsonNull }?.asInt == 1
        return Track(
            source = MusicSource.QQ,
            id = id,
            mid = mid,
            name = (o.get("name") ?: o.get("title") ?: o.get("songname"))?.asString ?: "",
            artists = singers,
            album = albumName,
            durationMs = interval * 1000,
            coverUrl = albumPmid?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" },
            vip = vip,
        )
    }

    /** 每次读取都递增，避免依赖 System.currentTimeMillis 的可测试性；用于 t/action/_ 等抗缓存参数。 */
    private var nonceCounter = 0L
    private val loginNonce: String
        get() = (System.currentTimeMillis() + nonceCounter++).toString()

    companion object {
        private val PTUI_CB = Regex("""ptuiCB\((.*?)\)""")
        private val PTUI_ARG = Regex("""'((?:\\.|[^'])*)'""")
        private val SIGX_RE = Regex("""[?&]ptsigx=([^&]+)""")
        private val UIN_RE = Regex("""[?&]uin=([^&]+)""")
        private val CODE_RE = Regex("""[?&]code=([^&]+)""")
    }
}
