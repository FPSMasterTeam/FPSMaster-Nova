package top.fpsmaster.web.api

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.stream.ChunkedWriteHandler
import top.fpsmaster.logger
import top.fpsmaster.web.network.NetworkManager

/**
 * WebSocket服务器
 *
 * 提供WebSocket通信服务，支持与UI界面的双向通信
 */
class WebSocketServer {
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null

    companion object {
        // 首选端口。被占用（例如另一个 Minecraft 实例）时按序向后顺延（见 start()）。
        const val DEFAULT_PORT = 4399
        // 端口冲突时最多顺延几个端口（4399..4418）。
        private const val MAX_PORT_PROBES = 20

        /**
         * WebSocket 服务实际绑定到的端口。UI 通过 HTTP 服务的 /api/ws-port 查询它再连接（浏览器
         * 无法写死端口）；同 JVM 内的调用方可直接读取。默认 [DEFAULT_PORT]。@Volatile 保证绑定
         * 线程写入后其它线程可见。
         */
        @Volatile
        var boundPort: Int = DEFAULT_PORT
            private set
    }

    /**
     * 启动WebSocket服务器
     */
    fun start() {
        logger.info("Starting WebSocket server (preferred port $DEFAULT_PORT)...")

        // netty-codec-http is only part of Minecraft's own Netty stack from the 4.2 era (1.21.11+); older
        // versions get it from our jar (see `bundledNettyCodecHttp` in build.gradle.kts). If it is missing,
        // bind() still succeeds and only initChannel fails — per connection, inside Netty — so the log would
        // claim the server started while every ClickGUI connect dies silently. Fail loudly here instead.
        try {
            Class.forName(
                "io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler",
                false,
                WebSocketServer::class.java.classLoader
            )
        } catch (e: Throwable) {
            logger.error(
                "netty-codec-http is missing from the runtime classpath — the ClickGUI WebSocket cannot " +
                    "accept connections. This build is missing its bundled netty-codec-http.",
                e
            )
            return
        }

        try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(4)

            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        val pipeline = ch.pipeline()

                        // HTTP编解码器，用于处理WebSocket握手
                        pipeline.addLast(HttpServerCodec())

                        // 分块写入处理器，用于发送大数据
                        pipeline.addLast(ChunkedWriteHandler())

                        // 聚合HTTP片段为完整的HTTP请求
                        pipeline.addLast(HttpObjectAggregator(65536))

                        // WebSocket协议处理器 - 处理握手和帧处理
                        pipeline.addLast(WebSocketServerProtocolHandler("/websocket"))

                        // 自定义消息处理器
                        pipeline.addLast(WebSocketFrameHandler())
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)

            // 首选 4399，被占用（BindException，例如另一个 Minecraft 实例）时向后顺延到下一个
            // 空闲端口，而不是让线程直接挂掉、UI 永远连不上。实际端口记入 boundPort 供 UI 查询。
            var future: ChannelFuture? = null
            for (offset in 0 until MAX_PORT_PROBES) {
                val candidate = DEFAULT_PORT + offset
                try {
                    future = bootstrap.bind("127.0.0.1", candidate).sync()
                    boundPort = candidate
                    if (offset > 0) {
                        logger.warn("WebSocket port {} was busy; fell back to port {}", DEFAULT_PORT, candidate)
                    }
                    break
                } catch (_: java.io.IOException) {
                    // 端口被占用（Netty 通过 sync() 抛出 BindException）→ 试下一个。
                }
            }
            if (future == null) {
                logger.error(
                    "WebSocket server failed to bind any port in {}..{}",
                    DEFAULT_PORT,
                    DEFAULT_PORT + MAX_PORT_PROBES - 1
                )
                return
            }
            logger.info("WebSocket server started successfully on port {}!", boundPort)

            // 等待服务器关闭
            future.channel().closeFuture().sync()
        } catch (e: InterruptedException) {
            logger.error("WebSocket server interrupted", e)
        } catch (e: Throwable) {
            logger.error("WebSocket server failed", e)
        } finally {
            shutdown()
        }
    }

    /**
     * 停止WebSocket服务器
     */
    fun stop() {
        logger.info("Stopping WebSocket server...")
        shutdown()
    }

    /**
     * 关闭服务器并清理资源
     */
    private fun shutdown() {
        NetworkManager.shutdown()
        bossGroup?.shutdownGracefully()
        workerGroup?.shutdownGracefully()
        logger.info("WebSocket server stopped")
    }

    /**
     * WebSocket帧处理器
     *
     * 处理WebSocket连接生命周期和消息收发
     */
    inner class WebSocketFrameHandler : SimpleChannelInboundHandler<TextWebSocketFrame>() {

        override fun channelActive(ctx: ChannelHandlerContext) {
            NetworkManager.addConnection(ctx)
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            NetworkManager.removeConnection(ctx)
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: TextWebSocketFrame) {
            val receivedText = msg.text()
            logger.debug("Received packet from ${ctx.channel().remoteAddress()}: $receivedText")

            // 委托给NetworkManager处理
            NetworkManager.handlePacket(ctx, receivedText)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.error("Exception in WebSocket connection with ${ctx.channel().remoteAddress()}", cause)
            ctx.close()
        }
    }
}