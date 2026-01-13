package top.fpsmaster.web.network

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import top.fpsmaster.logger
import top.fpsmaster.web.network.handler.NetworkContext
import top.fpsmaster.web.network.handler.PacketProcessor
import top.fpsmaster.web.network.packet.Packet
import top.fpsmaster.web.network.packet.PacketRegistry
import top.fpsmaster.web.network.serializer.PacketSerializer
import java.util.concurrent.ConcurrentHashMap

/**
 * 网络管理器
 *
 * 管理WebSocket连接和数据包收发
 */
object NetworkManager {
    private val connections = ConcurrentHashMap<ChannelHandlerContext, NetworkContext>()

    /**
     * 发送数据包到指定连接
     * @param ctx 目标连接上下文
     * @param packet 要发送的数据包
     */
    fun sendPacket(ctx: ChannelHandlerContext, packet: Packet) {
        try {
            val packetId = PacketRegistry.getPacketId(packet::class.java)
            if (packetId == null) {
                logger.error("Cannot send unregistered packet: ${packet::class.simpleName}")
                return
            }

            val json = PacketSerializer.serialize(packet, packetId)
            ctx.channel().writeAndFlush(TextWebSocketFrame(json))

            logger.debug("Sent packet ${packet::class.simpleName} (ID: $packetId) to ${ctx.channel().remoteAddress()}")
        } catch (e: Exception) {
            logger.error("Failed to send packet ${packet::class.simpleName}", e)
        }
    }

    /**
     * 发送数据包的便捷函数（用于客户端）
     * @param packet 要发送的数据包
     * @return 是否发送成功
     */
    fun sendPacketToServer(packet: Packet): Boolean {
        val client = top.fpsmaster.web.network.client.WebSocketClient.instance
        val packetId = PacketRegistry.getPacketId(packet::class.java)
        if (packetId == null) {
            logger.error("Cannot send unregistered packet: ${packet::class.simpleName}")
            return false
        }

        val json = PacketSerializer.serialize(packet, packetId)
        logger.debug("Sending packet ${packet::class.simpleName} (ID: $packetId) to server")
        return client.send(json)
    }

    /**
     * 广播数据包到所有连接
     * @param packet 要广播的数据包
     */
    fun broadcastPacket(packet: Packet) {
        connections.keys.forEach { ctx ->
            if (ctx.channel().isActive) {
                sendPacket(ctx, packet)
            }
        }
    }

    /**
     * 处理接收到的数据包
     * @param ctx 连接上下文
     * @param message 接收到的JSON消息
     */
    fun handlePacket(ctx: ChannelHandlerContext, message: String) {
        try {
            // 解析packetId
            val jsonElement = com.google.gson.Gson().fromJson(message, com.google.gson.JsonObject::class.java)
            val packetId = jsonElement.get("packetId")?.asInt

            if (packetId == null) {
                logger.warn("Received packet without packetId: $message")
                return
            }

            val context = connections.getOrPut(ctx) {
                NetworkContext(ctx, isServerSide = true)
            }

            PacketProcessor.processRaw(packetId, message, context)
        } catch (e: Exception) {
            logger.error("Failed to handle packet: $message", e)
        }
    }

    /**
     * 添加连接
     * @param ctx 连接上下文
     */
    fun addConnection(ctx: ChannelHandlerContext) {
        val context = NetworkContext(ctx, isServerSide = true)
        connections[ctx] = context
        logger.info("Client connected: ${ctx.channel().remoteAddress()}. Total connections: ${connections.size}")
    }

    /**
     * 移除连接
     * @param ctx 连接上下文
     */
    fun removeConnection(ctx: ChannelHandlerContext) {
        connections.remove(ctx)
        logger.info("Client disconnected: ${ctx.channel().remoteAddress()}. Total connections: ${connections.size}")
    }

    /**
     * 获取连接数量
     */
    fun getConnectionCount(): Int = connections.size

    /**
     * 关闭所有连接
     */
    fun shutdown() {
        connections.keys.forEach { it.close() }
        connections.clear()
        logger.info("Network manager shutdown, all connections closed")
    }
}
