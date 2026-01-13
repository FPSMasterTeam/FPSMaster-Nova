package top.fpsmaster.web.network

import io.netty.channel.ChannelHandlerContext
import top.fpsmaster.web.api.LocalServer
import top.fpsmaster.web.network.handler.NetworkContext
import top.fpsmaster.web.network.packets.*
import top.fpsmaster.web.network.packet.PacketRegistry
import top.fpsmaster.web.network.handler.PacketProcessor

/**
 * WebSocket通信协议框架 - 使用示例
 *
 * 演示如何使用本框架进行UI与Minecraft进程间的通信
 */
object PacketFrameworkExample {

    /**
     * 示例1：启动服务器
     */
    fun startServer() {
        // 初始化数据包注册
        PacketRegistryInitializer.initialize()

        // 启动本地服务器（HTTP + WebSocket）
        val server = LocalServer()
        server.start()
    }

    /**
     * 示例2：发送数据包
     */
    fun sendPacketExample(context: ChannelHandlerContext) {
        // 创建握手数据包
        val handshake = HandshakePacket().apply {
            protocolVersion = 1
            clientVersion = "1.0.0"
        }

        // 发送数据包
        NetworkManager.sendPacket(context, handshake)
    }

    /**
     * 示例3：广播数据包到所有连接的客户端
     */
    fun broadcastPacketExample() {
        // 创建日志消息数据包
        val logPacket = LogMessagePacket().apply {
            level = LogMessagePacket.LogLevel.INFO
            message = "Server started successfully!"
            timestamp = System.currentTimeMillis()
        }

        // 广播到所有客户端
        NetworkManager.broadcastPacket(logPacket)
    }

    /**
     * 示例4：注册自定义数据包
     */
    fun registerCustomPacket() {
        // 1. 定义自定义数据包类
        class CustomPacket : top.fpsmaster.web.network.packet.ClientboundPacket() {
            var customData: String = ""

            override fun write(buffer: top.fpsmaster.web.network.serializer.PacketBuffer) {
                buffer.writeString(customData)
            }

            override fun read(buffer: top.fpsmaster.web.network.serializer.PacketBuffer) {
                customData = buffer.readString() ?: ""
            }
        }

        // 2. 注册数据包
        PacketRegistry.registerPacket { CustomPacket() }

        // 3. 注册处理器
        PacketProcessor.registerHandler<CustomPacket> { packet, context ->
            println("Received custom packet: ${packet.customData}")
        }
    }

    /**
     * 示例5：处理数据包
     */
    fun handlePacketExample() {
        // 使用Kotlin协程处理器
        PacketProcessor.registerHandler<PlayerInfoPacket> { packet, context ->
            // 异步处理数据包
            println("Player: ${packet.playerName}")
            println("Health: ${packet.health}")
            println("Position: ${packet.position}")

            // 可以发送响应
            // NetworkManager.sendPacket(context.channelHandlerContext, responsePacket)
        }
    }

    /**
     * 示例6：完整的通信流程
     */
    fun fullCommunicationFlow() {
        // 1. 初始化
        PacketRegistryInitializer.initialize()

        // 2. 启动服务器
        val server = LocalServer()
        server.start()

        // 3. 等待客户端连接...

        // 4. 处理客户端请求（在处理器中自动完成）
    }
}

/**
 * 快速上手指南
 *
 * ## 1. 定义数据包
 * ```
 * class MyPacket : ClientboundPacket() {
 *     var data: String = ""
 *
 *     override fun write(buffer: PacketBuffer) {
 *         buffer.writeString(data)
 *     }
 *
 *     override fun read(buffer: PacketBuffer) {
 *         data = buffer.readString() ?: ""
 *     }
 * }
 * ```
 *
 * ## 2. 注册数据包
 * ```
 * PacketRegistry.registerPacket { MyPacket() }
 * ```
 *
 * ## 3. 注册处理器
 * ```
 * PacketProcessor.registerHandler<MyPacket> { packet, context ->
 *     // 处理数据包
 *     println("Received: ${packet.data}")
 * }
 * ```
 *
 * ## 4. 启动服务器
 * ```
 * PacketRegistryInitializer.initialize()
 * LocalServer().start()
 * ```
 *
 * ## 5. 发送数据包
 * ```
 * val packet = MyPacket().apply { data = "Hello" }
 * NetworkManager.sendPacket(channelContext, packet)
 * ```
 */
