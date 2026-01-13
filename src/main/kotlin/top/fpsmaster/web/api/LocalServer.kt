package top.fpsmaster.web.api

import com.sun.net.httpserver.HttpServer
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

            // 根路径
            httpServer?.createContext("/") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }

            // 刷新路径
            httpServer?.createContext("/refresh") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("OK".toByteArray())
                exchange.responseBody.flush()
                exchange.responseBody.close()
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
}
