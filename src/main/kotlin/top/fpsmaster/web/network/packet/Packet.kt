package top.fpsmaster.web.network.packet

import top.fpsmaster.web.network.serializer.PacketBuffer

/**
 * 所有网络数据包的基础接口
 *
 * 实现此接口以创建自定义数据包类型
 */
interface Packet {
    /**
     * 将数据包序列化到缓冲区
     * @param buffer 数据缓冲区
     */
    fun write(buffer: PacketBuffer)

    /**
     * 从缓冲区反序列化数据包
     * @param buffer 数据缓冲区
     */
    fun read(buffer: PacketBuffer)

    /**
     * 获取数据包的处理方（客户端还是服务端）
     */
    fun getDirection(): PacketDirection
}

/**
 * 客户端到服务端的数据包基类
 */
abstract class ClientboundPacket : Packet {
    override fun getDirection(): PacketDirection = PacketDirection.CLIENT_TO_SERVER
}

/**
 * 服务端到客户端的数据包基类
 */
abstract class ServerboundPacket : Packet {
    override fun getDirection(): PacketDirection = PacketDirection.SERVER_TO_CLIENT
}

/**
 * 双向数据包基类
 */
abstract class BidirectionalPacket : Packet {
    override fun getDirection(): PacketDirection = PacketDirection.BIDIRECTIONAL
}
