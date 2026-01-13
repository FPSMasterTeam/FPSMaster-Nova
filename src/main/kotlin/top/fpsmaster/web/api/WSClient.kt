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
    private val port = 4399
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null

    /**
     * 启动WebSocket服务器
     */
    fun start() {
        logger.info("Starting WebSocket server on port $port...")

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

            val future = bootstrap.bind(port).sync()
            logger.info("WebSocket server started successfully on port $port!")

            // 等待服务器关闭
            future.channel().closeFuture().sync()
        } catch (e: InterruptedException) {
            logger.error("WebSocket server interrupted", e)
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