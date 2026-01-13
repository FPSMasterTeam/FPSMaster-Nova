package top.fpsmaster.web.network.handler

import top.fpsmaster.logger
import top.fpsmaster.web.network.packet.Packet
import top.fpsmaster.web.network.packet.PacketRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

/**
 * 数据包处理器分发器
 *
 * 负责将接收到的数据包分发到对应的处理器
 */
object PacketProcessor {
    private val handlers = ConcurrentHashMap<Class<out Packet>, suspend (Packet, NetworkContext) -> Unit>()
    private val dispatcherScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 注册数据包处理器
     * @param packetClass 数据包类
     * @param handler 处理器函数
     */
    fun <T : Packet> registerHandler(
        packetClass: Class<T>,
        handler: suspend (T, NetworkContext) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        handlers[packetClass] = handler as suspend (Packet, NetworkContext) -> Unit
        logger.info("Registered handler for packet: ${packetClass.simpleName}")
    }

    /**
     * 注册数据包处理器（Kotlin扩展函数）
     * @param handler 处理器函数
     */
    inline fun <reified T : Packet> registerHandler(noinline handler: suspend (T, NetworkContext) -> Unit) {
        registerHandler(T::class.java, handler)
    }

    /**
     * 处理数据包
     * @param packet 数据包实例
     * @param context 网络上下文
     */
    fun process(packet: Packet, context: NetworkContext) {
        val handler = handlers[packet::class.java]
        if (handler == null) {
            logger.warn("No handler registered for packet: ${packet::class.simpleName}")
            return
        }

        // 在协程中异步处理
        dispatcherScope.launch {
            try {
                handler(packet, context)
            } catch (e: Exception) {
                logger.error("Error processing packet ${packet::class.simpleName}", e)
            }
        }
    }

    /**
     * 处理原始数据（从WebSocket接收）
     * @param packetId 数据包ID
     * @param jsonData JSON数据
     * @param context 网络上下文
     */
    fun processRaw(packetId: Int, jsonData: String, context: NetworkContext) {
        try {
            val packet = PacketRegistry.createPacket(packetId)
            val deserialized = top.fpsmaster.web.network.serializer.PacketSerializer.deserialize(
                jsonData,
                PacketRegistry::createPacket
            )

            deserialized?.let { process(it, context) }
                ?: logger.warn("Failed to deserialize packet with ID: $packetId")
        } catch (e: Exception) {
            logger.error("Error processing raw packet data with ID: $packetId", e)
        }
    }

    /**
     * 移除数据包处理器
     * @param packetClass 数据包类
     */
    fun <T : Packet> unregisterHandler(packetClass: Class<T>) {
        handlers.remove(packetClass)
        logger.info("Unregistered handler for packet: ${packetClass.simpleName}")
    }

    /**
     * 清空所有处理器
     */
    fun clear() {
        handlers.clear()
        dispatcherScope.cancel()
    }
}
