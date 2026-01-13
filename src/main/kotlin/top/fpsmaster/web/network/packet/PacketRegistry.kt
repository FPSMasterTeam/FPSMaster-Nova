package top.fpsmaster.web.network.packet

import top.fpsmaster.logger
import top.fpsmaster.web.network.serializer.PacketSerializationException
import java.util.concurrent.ConcurrentHashMap

/**
 * 数据包注册表
 *
 * 管理数据包类型和ID的映射关系，线程安全
 */
object PacketRegistry {
    private val packetIdMap = ConcurrentHashMap<Class<out Packet>, Int>()
    private val packetClassMap = ConcurrentHashMap<Int, Class<out Packet>>()
    private val packetConstructors = ConcurrentHashMap<Class<out Packet>, () -> Packet>()

    private var nextId = 0

    /**
     * 注册数据包类型
     * @param packetClass 数据包类
     * @param supplier 数据包实例创建函数
     * @return 分配的数据包ID
     */
    fun registerPacket(
        packetClass: Class<out Packet>,
        supplier: () -> Packet
    ): Int {
        if (packetIdMap.containsKey(packetClass)) {
            throw IllegalStateException("Packet ${packetClass.simpleName} is already registered!")
        }

        val id = nextId++
        packetIdMap[packetClass] = id
        packetClassMap[id] = packetClass
        packetConstructors[packetClass] = supplier

        logger.info("Registered packet: ${packetClass.simpleName} with ID: $id")
        return id
    }

    /**
     * 注册数据包类型（Kotlin扩展函数）
     * @param supplier 数据包实例创建函数
     * @return 分配的数据包ID
     */
    inline fun <reified T : Packet> registerPacket(noinline supplier: () -> T): Int {
        return registerPacket(T::class.java, supplier)
    }

    /**
     * 获取数据包ID
     * @param packetClass 数据包类
     * @return 数据包ID，如果未注册则返回null
     */
    fun getPacketId(packetClass: Class<out Packet>): Int? = packetIdMap[packetClass]

    /**
     * 获取数据包类
     * @param packetId 数据包ID
     * @return 数据包类，如果未注册则返回null
     */
    fun getPacketClass(packetId: Int): Class<out Packet>? = packetClassMap[packetId]

    /**
     * 创建数据包实例
     * @param packetId 数据包ID
     * @return 数据包实例
     * @throws PacketSerializationException 如果数据包ID未注册
     */
    fun createPacket(packetId: Int): Packet {
        val packetClass = packetClassMap[packetId]
            ?: throw PacketSerializationException("Unknown packet ID: $packetId")

        val supplier = packetConstructors[packetClass]
            ?: throw PacketSerializationException("No constructor found for packet: ${packetClass.simpleName}")

        return supplier()
    }

    /**
     * 检查数据包是否已注册
     * @param packetClass 数据包类
     * @return 是否已注册
     */
    fun isRegistered(packetClass: Class<out Packet>): Boolean = packetIdMap.containsKey(packetClass)

    /**
     * 检查数据包ID是否已注册
     * @param packetId 数据包ID
     * @return 是否已注册
     */
    fun isRegistered(packetId: Int): Boolean = packetClassMap.containsKey(packetId)

    /**
     * 获取所有已注册的数据包数量
     */
    fun getRegisteredPacketCount(): Int = packetIdMap.size

    /**
     * 清空所有注册的数据包（主要用于测试）
     */
    @Synchronized
    fun clear() {
        packetIdMap.clear()
        packetClassMap.clear()
        packetConstructors.clear()
        nextId = 0
    }
}
