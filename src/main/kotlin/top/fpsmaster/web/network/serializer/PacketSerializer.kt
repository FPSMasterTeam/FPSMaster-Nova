package top.fpsmaster.web.network.serializer

import top.fpsmaster.web.network.packet.Packet
import java.util.Base64

/**
 * 数据包序列化器
 *
 * 负责将Packet对象序列化为可在WebSocket上传输的格式
 * 协议格式: JSON字符串，包含packetId和data字段
 * - packetId: 数据包ID（整数）
 * - data: Base64编码的二进制数据
 */
object PacketSerializer {

    private val gson = com.google.gson.Gson()

    /**
     * 序列化数据包
     * @param packet 要序列化的数据包
     * @param packetId 数据包ID
     * @return JSON字符串
     */
    fun serialize(packet: Packet, packetId: Int): String {
        val buffer = PacketBuffer()
        packet.write(buffer)

        val packetData = PacketData(
            packetId = packetId,
            data = Base64.getEncoder().encodeToString(buffer.toByteArray())
        )

        return gson.toJson(packetData)
    }

    /**
     * 反序列化数据包
     * @param json JSON字符串
     * @param packetSupplier 根据packetId创建对应数据包实例的函数
     * @return 反序列化的数据包
     */
    fun deserialize(json: String, packetSupplier: (Int) -> Packet?): Packet? {
        try {
            val packetData = gson.fromJson(json, PacketData::class.java)
            val packet = packetSupplier(packetData.packetId) ?: return null

            val bytes = Base64.getDecoder().decode(packetData.data)
            val buffer = PacketBuffer.fromBytes(bytes)

            packet.read(buffer)
            return packet
        } catch (e: Exception) {
            throw PacketSerializationException("Failed to deserialize packet: ${e.message}", e)
        }
    }

    /**
     * 数据包数据结构
     */
    private data class PacketData(
        val packetId: Int,
        val data: String
    )
}

/**
 * 数据包序列化异常
 */
class PacketSerializationException(message: String, cause: Throwable? = null) : Exception(message, cause)
