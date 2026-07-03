package top.fpsmaster.web.music.http

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * 轻量 HTTP 助手，基于 JDK 内置 [HttpClient]，零第三方依赖。
 *
 * 特意关闭自动重定向（QQ 登录流程需要读取 302 的 Location 与 Set-Cookie），
 * 并暴露原始响应头，便于手动处理 cookie。
 */
class MusicHttp {

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    data class Response(
        val status: Int,
        val body: String,
        val bytes: ByteArray,
        val headers: Map<String, List<String>>,
    ) {
        /**
         * 从响应头解析出的 Set-Cookie（cookie 名 -> 值）。
         *
         * **跳过空值**：同一响应里常出现同名 cookie 一次真值、一次清空（`p_skey=xxx` 与 `p_skey=`），
         * 若让空值覆盖真值会丢掉关键会话 cookie（QQ 登录 p_skey 就栽在这）。故只记录非空值。
         */
        val setCookies: Map<String, String> by lazy {
            val out = LinkedHashMap<String, String>()
            headers.entries.firstOrNull { it.key.equals("set-cookie", true) }?.value?.forEach { raw ->
                val pair = raw.substringBefore(';')
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isNotEmpty() && value.isNotEmpty()) out[name] = value
            }
            out
        }

        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
    }

    fun get(
        url: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
    ): Response {
        val full = if (query.isEmpty()) url else "$url?${encodeForm(query)}"
        val builder = HttpRequest.newBuilder(URI.create(full)).GET()
        applyHeaders(builder, headers, cookies)
        return send(builder)
    }

    fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
    ): Response {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
        applyHeaders(builder, headers, cookies)
        return send(builder)
    }

    fun postJson(
        url: String,
        json: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
    ): Response {
        val full = if (query.isEmpty()) url else "$url?${encodeForm(query)}"
        val builder = HttpRequest.newBuilder(URI.create(full))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        applyHeaders(builder, headers, cookies)
        return send(builder)
    }

    private fun applyHeaders(
        builder: HttpRequest.Builder,
        headers: Map<String, String>,
        cookies: Map<String, String>,
    ) {
        builder.timeout(Duration.ofSeconds(20))
        if ("User-Agent" !in headers) {
            builder.header("User-Agent", DEFAULT_UA)
        }
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
    }

    private fun send(builder: HttpRequest.Builder): Response {
        val resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        val bytes = resp.body()
        return Response(
            status = resp.statusCode(),
            body = String(bytes, StandardCharsets.UTF_8),
            bytes = bytes,
            headers = resp.headers().map(),
        )
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

        fun encodeForm(params: Map<String, String>): String =
            params.entries.joinToString("&") {
                "${enc(it.key)}=${enc(it.value)}"
            }

        private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)
    }
}
