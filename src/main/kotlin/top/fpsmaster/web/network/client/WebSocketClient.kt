package top.fpsmaster.web.network.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import top.fpsmaster.logger
import top.fpsmaster.web.network.NetworkManager
import java.net.URI

/**
 * WebSocket客户端
 *
 * 用于连接到本地WebSocket服务器，与UI进行通信
 */
class WebSocketClient(
    private val uri: String = "ws://localhost:4399/websocket",
    private val onMessageReceived: (String) -> Unit = {}
) {
    private var channel: Channel? = null
    private var workerGroup: EventLoopGroup? = null

    private val isConnected: Boolean
        get() = channel?.isActive == true

    /**
     * 连接到WebSocket服务器
     */
    fun connect() {
        if (isConnected) {
            logger.warn("WebSocket client already connected")
            return
        }

        logger.info("Connecting to WebSocket server: $uri")

        try {
            workerGroup = NioEventLoopGroup()

            val bootstrap = Bootstrap()
            bootstrap.group(workerGroup)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        val pipeline = ch.pipeline()

                        // HTTP编解码器
                        pipeline.addLast(HttpClientCodec())

                        // 聚合HTTP片段
                        pipeline.addLast(HttpObjectAggregator(8192))

                        // WebSocket客户端协议处理器
                        val uri = URI(this@WebSocketClient.uri)
                        pipeline.addLast(
                            WebSocketClientProtocolHandler(
                                uri,
                                WebSocketVersion.V13,
                                null,
                                true,
                                null,
                                65536
                            )
                        )

                        // 自定义消息处理器
                        pipeline.addLast(ClientHandler())
                    }
                })

            // 连接到服务器
            val endpoint = URI(uri)
            val host = endpoint.host ?: "localhost"
            val port = if (endpoint.port != -1) endpoint.port else 4399
            val future = bootstrap.connect(host, port).sync()
            channel = future.channel()

            logger.info("WebSocket client connected successfully!")
        } catch (e: Exception) {
            logger.error("Failed to connect to WebSocket server", e)
            disconnect()
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        logger.info("Disconnecting WebSocket client...")
        channel?.close()
        workerGroup?.shutdownGracefully()
        channel = null
    }

    /**
     * 发送文本消息
     */
    fun send(message: String): Boolean {
        if (!isConnected) {
            logger.warn("Cannot send message, client not connected")
            return false
        }

        try {
            channel?.writeAndFlush(TextWebSocketFrame(message))
            logger.debug("Sent message: $message")
            return true
        } catch (e: Exception) {
            logger.error("Failed to send message", e)
            return false
        }
    }

    /**
     * 客户端消息处理器
     */
    inner class ClientHandler : SimpleChannelInboundHandler<TextWebSocketFrame>() {

        override fun channelActive(ctx: ChannelHandlerContext) {
            logger.info("WebSocket connection established: ${ctx.channel().remoteAddress()}")
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            logger.info("WebSocket connection closed: ${ctx.channel().remoteAddress()}")
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: TextWebSocketFrame) {
            val message = msg.text()
            logger.debug("Received message from server: $message")

            // 回调处理接收到的消息
            try {
                onMessageReceived(message)
                // 同时交给NetworkManager处理
                NetworkManager.handlePacket(ctx, message)
            } catch (e: Exception) {
                logger.error("Error handling received message", e)
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.error("Exception in WebSocket client", cause)
            ctx.close()
        }
    }

    companion object {
        /**
         * 单例客户端实例
         */
        @JvmStatic
        val instance: WebSocketClient by lazy {
            WebSocketClient(
                uri = "ws://localhost:4399/websocket"
            )
        }
    }
}
