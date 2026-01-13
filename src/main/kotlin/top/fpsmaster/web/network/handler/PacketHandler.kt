package top.fpsmaster.web.network.handler

import io.netty.channel.ChannelHandlerContext
import top.fpsmaster.web.network.packet.Packet

/**
 * 数据包处理器接口
 *
 * 用于处理接收到的数据包
 */
interface PacketHandler<T : Packet> {
    /**
     * 处理数据包
     * @param packet 数据包实例
     * @param context 网络上下文
     */
    suspend fun handle(packet: T, context: NetworkContext)
}

/**
 * 网络上下文
 *
 * 提供网络通信的上下文信息和操作
 */
class NetworkContext(
    val channelHandlerContext: ChannelHandlerContext?,
    val isServerSide: Boolean
) {
    /**
     * 发送数据包
     * @param packet 要发送的数据包
     */
    fun sendPacket(packet: Packet) {
        channelHandlerContext?.let {
            // 这里将在NetworkManager中实现
            throw UnsupportedOperationException("Use NetworkManager.sendPacket() instead")
        }
    }

    /**
     * 关闭连接
     */
    fun close() {
        channelHandlerContext?.close()
    }

    /**
     * 检查连接是否活跃
     */
    fun isActive(): Boolean = channelHandlerContext?.channel()?.isActive ?: false
}

/**
 * 数据包处理器包装类
 *
 * 将处理器函数包装成PacketHandler对象
 */
class FunctionalPacketHandler<T : Packet>(
    private val handler: suspend (T, NetworkContext) -> Unit
) : PacketHandler<T> {
    override suspend fun handle(packet: T, context: NetworkContext) {
        handler(packet, context)
    }
}

/**
 * 创建数据包处理器的便捷函数
 */
inline fun <reified T : Packet> packetHandler(
    crossinline handler: suspend (T, NetworkContext) -> Unit
): PacketHandler<T> {
    return FunctionalPacketHandler { packet, context ->
        handler(packet, context)
    }
}
