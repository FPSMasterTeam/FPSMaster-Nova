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
     */
    private fun startHttpServer() {
        try {
            httpServer = HttpServer.create(InetSocketAddress("localhost", 7781), 0)
            httpExecutor = Executors.newCachedThreadPool()

            // 刷新路径
            httpServer?.createContext("/refresh") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("OK".toByteArray())
                exchange.responseBody.flush()
                exchange.responseBody.close()
            }

            // 音乐 API 路由（/api/netease/*, /api/qq/*）
            httpServer?.let { musicRoutes.register(it) }

            httpServer?.createContext("/") { exchange ->
                serveStaticContent(exchange)
            }

            httpServer?.executor = httpExecutor
            httpServer?.start()

            logger.info("HTTP server started on port 7781")
        } catch (e: Exception) {
            logger.error("Failed to start HTTP server", e)
        }
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
