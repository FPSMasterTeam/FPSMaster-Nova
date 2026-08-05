package top.fpsmaster.web.api

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import top.fpsmaster.logger
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * 本地HTTP服务器
 *
 * 提供简单的HTTP服务，配合WebSocket服务器使用
 */
class LocalServer {
    private val webSocketServer = WebSocketServer()
    private val musicRoutes = MusicRoutes()
    private var httpServer: HttpServer? = null
    private var httpExecutor: java.util.concurrent.ExecutorService? = null

    companion object {
        // 内置 Web UI 的首选端口。被占用时按序向后顺延（见 bindHttpServer）。
        const val DEFAULT_PORT = 7781
        // 端口冲突时最多顺延几个端口（7781..7800）。
        private const val MAX_PORT_PROBES = 20

        /**
         * 内置 HTTP 服务实际绑定到的端口。BasicBrowser 据此拼 WebView 的 URL。默认值为
         * [DEFAULT_PORT]，这样在服务启动前就读取它的调用方也能拿到合理值。@Volatile 保证
         * 启动线程写入后其它线程可见。
         */
        @Volatile
        var boundPort: Int = DEFAULT_PORT
            private set
    }

    // Identity endpoint (see startHttpServer). Sourced from BasicBrowser so the path/marker can only
    // ever be defined in one place and the dev-server probe on port 3000 stays in agreement with it.
    private val NOVA_IDENTITY_PATH = top.fpsmaster.web.BasicBrowser.NOVA_IDENTITY_PATH
    private val NOVA_IDENTITY_BODY =
        "{\"app\":\"${top.fpsmaster.web.BasicBrowser.NOVA_IDENTITY_MARKER}\",\"service\":\"local-server\"}"

    /**
     * 启动本地服务器（HTTP + WebSocket）
     */
    fun start() {
        logger.info("Starting local server...")

        // 启动HTTP服务器
        startHttpServer()

        // 启动WebSocket服务器
        startWebSocketServer()

        logger.info("Local server started successfully!")
    }

    /**
     * 启动HTTP服务器
     *
     * 端口 7781 被占用时（例如上一进程没退干净、或用户本机别的服务占了这个端口），自动向后
     * 顺延到下一个空闲端口，而不是像以前那样直接失败、让 WebView 打到一个空/错的端口。实际
     * 绑定到的端口写入 [boundPort]，供 BasicBrowser 拼 WebView URL；页面内的 /api 调用走相对
     * 路径，天然跟随实际端口，无需再关心具体端口号。
     */
    private fun startHttpServer() {
        try {
            val server = bindHttpServer()
            if (server == null) {
                logger.error(
                    "Failed to start HTTP server: no free port in {}..{}",
                    DEFAULT_PORT,
                    DEFAULT_PORT + MAX_PORT_PROBES - 1
                )
                return
            }
            httpServer = server
            httpExecutor = Executors.newCachedThreadPool()

            // 刷新路径
            httpServer?.createContext("/refresh") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("OK".toByteArray())
                exchange.responseBody.flush()
                exchange.responseBody.close()
            }

            // 身份端点：唯一标识"这是 Nova 自己的 Web UI 服务"。dev 环境下 BasicBrowser 会探测
            // 端口 3000 上的同名端点（由 Vite 中间件提供）来确认那是我们的 dev server，而不是别的
            // 恰好占用 3000 的本地服务（例如某个 NestJS/Express 应用）。marker 必须与
            // BasicBrowser.NOVA_IDENTITY_MARKER 及 ui/vite.config.ts 保持一致。
            httpServer?.createContext(NOVA_IDENTITY_PATH) { exchange ->
                val body = NOVA_IDENTITY_BODY.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            // WebSocket 端口发现：WS 服务端口（默认 4399）被占时也会自动顺延，浏览器无法写死，
            // 因此通过这个端点查询实际端口。走 /api 前缀，dev 下由 Vite 代理转发。
            httpServer?.createContext("/api/ws-port") { exchange ->
                val body = "{\"port\":${WebSocketServer.boundPort}}".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            // 音乐 API 路由（/api/netease/*, /api/qq/*）
            httpServer?.let { musicRoutes.register(it) }

            httpServer?.createContext("/") { exchange ->
                serveStaticContent(exchange)
            }

            httpServer?.executor = httpExecutor
            httpServer?.start()

            logger.info("HTTP server started on port {}", boundPort)
        } catch (e: Exception) {
            logger.error("Failed to start HTTP server", e)
        }
    }

    /**
     * 从 [DEFAULT_PORT] 开始，最多尝试 [MAX_PORT_PROBES] 个端口，返回第一个成功绑定的
     * [HttpServer]（并记录到 [boundPort]）；全部被占用则返回 null。
     */
    private fun bindHttpServer(): HttpServer? {
        for (offset in 0 until MAX_PORT_PROBES) {
            val port = DEFAULT_PORT + offset
            try {
                val server = HttpServer.create(InetSocketAddress("localhost", port), 0)
                boundPort = port
                if (offset > 0) {
                    logger.warn("Port {} was busy; bundled UI server fell back to port {}", DEFAULT_PORT, port)
                }
                return server
            } catch (_: java.io.IOException) {
                // 端口被占用（BindException）或不可绑定 → 试下一个。
            }
        }
        return null
    }

    /**
     * 启动WebSocket服务器（在后台线程）
     */
    private fun startWebSocketServer() {
        Thread {
            webSocketServer.start()
        }.apply {
            name = "WebSocket Server Thread"
            isDaemon = true
            start()
        }
    }

    /**
     * 停止本地服务器
     */
    fun stop() {
        logger.info("Stopping local server...")

        httpServer?.stop(0)
        httpExecutor?.shutdown()
        webSocketServer.stop()

        logger.info("Local server stopped")
    }

    private fun serveStaticContent(exchange: HttpExchange) {
        val requestPath = exchange.requestURI.path.removePrefix("/")
        val resourcePath = when {
            requestPath.isBlank() -> "index.html"
            else -> requestPath
        }

        val servedResourcePath = if (readBundledUi(resourcePath) != null) {
            resourcePath
        } else if (!resourcePath.contains('.')) {
            "index.html"
        } else {
            ""
        }
        val responseBytes = if (servedResourcePath.isNotEmpty()) readBundledUi(servedResourcePath) else null

        if (responseBytes == null) {
            exchange.sendResponseHeaders(404, 0)
            exchange.responseBody.close()
            return
        }

        exchange.responseHeaders.add("Content-Type", contentType(servedResourcePath))
        exchange.sendResponseHeaders(200, responseBytes.size.toLong())
        exchange.responseBody.use { body ->
            body.write(responseBytes)
        }
    }

    private fun readBundledUi(resourcePath: String): ByteArray? {
        val normalizedPath = resourcePath.trimStart('/')
        return LocalServer::class.java.classLoader
            .getResourceAsStream("webui/$normalizedPath")
            ?.use { it.readBytes() }
    }

    private fun contentType(resourcePath: String): String {
        return when {
            resourcePath.endsWith(".html") -> "text/html; charset=UTF-8"
            resourcePath.endsWith(".js") -> "application/javascript; charset=UTF-8"
            resourcePath.endsWith(".css") -> "text/css; charset=UTF-8"
            resourcePath.endsWith(".json") -> "application/json; charset=UTF-8"
            resourcePath.endsWith(".svg") -> "image/svg+xml"
            resourcePath.endsWith(".png") -> "image/png"
            resourcePath.endsWith(".jpg") || resourcePath.endsWith(".jpeg") -> "image/jpeg"
            resourcePath.endsWith(".webp") -> "image/webp"
            resourcePath.endsWith(".ico") -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }
}
