package top.fpsmaster.web.network.packets

import top.fpsmaster.logger
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.value.Value
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.web.BasicBrowser
import top.fpsmaster.web.network.NetworkManager
import top.fpsmaster.web.network.handler.PacketProcessor
import top.fpsmaster.web.network.packet.PacketRegistry

/**
 * 数据包注册初始化器
 *
 * 负责注册所有数据包类型和处理器
 */
object PacketRegistryInitializer {

    /**
     * 初始化所有数据包注册
     */
    fun initialize() {
        logger.info("Initializing packet registry...")

        registerPackets()
        registerHandlers()

        logger.info("Packet registry initialized with ${PacketRegistry.getRegisteredPacketCount()} packets")
    }

    /**
     * 注册所有数据包类型
     */
    private fun registerPackets() {
        // 握手相关
        PacketRegistry.registerPacket { HandshakePacket() }
        PacketRegistry.registerPacket { HandshakeResponsePacket() }

        // 心跳
        PacketRegistry.registerPacket { HeartbeatPacket() }

        // 命令相关
        PacketRegistry.registerPacket { ExecuteCommandPacket() }
        PacketRegistry.registerPacket { CommandResponsePacket() }

        // 玩家信息
        PacketRegistry.registerPacket { PlayerInfoRequestPacket() }
        PacketRegistry.registerPacket { PlayerInfoPacket() }

        // 日志和事件
        PacketRegistry.registerPacket { LogMessagePacket() }
        PacketRegistry.registerPacket { UIEventPacket() }

        // GUI加载事件
        PacketRegistry.registerPacket { GuiLoadEventPacket() }
        PacketRegistry.registerPacket { GuiLoadAckPacket() }

        // 模块同步
        PacketRegistry.registerPacket { ModuleListRequestPacket() }
        PacketRegistry.registerPacket { ModuleListPacket() }
        PacketRegistry.registerPacket { ModuleTogglePacket() }
        PacketRegistry.registerPacket { ModuleValueUpdatePacket() }
    }

    /**
     * 注册所有数据包处理器
     */
    private fun registerHandlers() {
        // 握手处理器
        PacketProcessor.registerHandler<HandshakePacket> { packet, context ->
            logger.info("Received handshake: ${packet.clientVersion}")
            // TODO: 实现握手逻辑
        }

        // 心跳处理器
        PacketProcessor.registerHandler<HeartbeatPacket> { packet, context ->
            logger.debug("Received heartbeat at ${packet.timestamp}")
            // 可以回复心跳
        }

        // 命令执行处理器
        PacketProcessor.registerHandler<ExecuteCommandPacket> { packet, context ->
            logger.info("Executing command: ${packet.command}")
            // TODO: 实现命令执行逻辑
        }

        // 玩家信息请求处理器
        PacketProcessor.registerHandler<PlayerInfoRequestPacket> { packet, context ->
            logger.debug("Player info requested")
            // TODO: 获取玩家信息并发送PlayerInfoPacket
        }

        // UI事件处理器
        PacketProcessor.registerHandler<UIEventPacket> { packet, context ->
            logger.info("Received UI event: ${packet.eventType}")
            // TODO: 处理UI事件
        }

        // GUI加载事件处理器（从UI接收）
        PacketProcessor.registerHandler<GuiLoadEventPacket> { packet, context ->
            logger.info("Received GUI load event: ${packet.eventType}")
            // 事件仅用于触发UI动画，ACK由前端回传
        }

        // GUI加载ACK处理器（从服务器接收）
        PacketProcessor.registerHandler<GuiLoadAckPacket> { packet, context ->
            logger.info("Received GUI load ACK: ${packet.message}")
            // 将ACK传递给当前的BasicBrowser实例
            BasicBrowser.handleAck(packet)
        }

        PacketProcessor.registerHandler<ModuleListRequestPacket> { _, context ->
            logger.info("Received module list request")
            context.channelHandlerContext?.let { channelContext ->
                NetworkManager.sendPacket(channelContext, createModuleListPacket())
            }
        }

        PacketProcessor.registerHandler<ModuleTogglePacket> { packet, _ ->
            val module = ModuleManager.modules[packet.moduleId.lowercase()]
            if (module == null) {
                logger.warn("Received toggle request for unknown module: ${packet.moduleId}")
                return@registerHandler
            }

            module.enabled = packet.enabled
            logger.info("Updated module ${module.identity} enabled=${module.enabled}")
            broadcastModuleSnapshot()
        }

        PacketProcessor.registerHandler<ModuleValueUpdatePacket> { packet, _ ->
            val module = ModuleManager.modules[packet.moduleId.lowercase()]
            if (module == null) {
                logger.warn("Received value update for unknown module: ${packet.moduleId}")
                return@registerHandler
            }

            val value = module.values.firstOrNull { it.getIdentity().equals(packet.valueId, ignoreCase = true) }
            if (value == null) {
                logger.warn("Received value update for unknown value: ${packet.moduleId}.${packet.valueId}")
                return@registerHandler
            }

            when (value) {
                is OptionValue -> {
                    if (packet.type != ModuleValueType.BOOLEAN) {
                        logger.warn("Type mismatch for ${module.identity}.${value.getIdentity()}: expected BOOLEAN, got ${packet.type}")
                        return@registerHandler
                    }
                    value.setValue(packet.booleanValue)
                }

                is NumberValue -> {
                    if (packet.type != ModuleValueType.NUMBER) {
                        logger.warn("Type mismatch for ${module.identity}.${value.getIdentity()}: expected NUMBER, got ${packet.type}")
                        return@registerHandler
                    }
                    value.setValue(packet.numberValue)
                }

                is StringValue -> {
                    if (packet.type != ModuleValueType.STRING) {
                        logger.warn("Type mismatch for ${module.identity}.${value.getIdentity()}: expected STRING, got ${packet.type}")
                        return@registerHandler
                    }
                    value.setValue(packet.stringValue ?: "")
                }

                else -> {
                    logger.warn("Unsupported value type for ${module.identity}.${value.getIdentity()}: ${value::class.simpleName}")
                    return@registerHandler
                }
            }

            logger.info("Updated value ${module.identity}.${value.getIdentity()}")
            broadcastModuleSnapshot()
        }
    }

    @JvmStatic
    fun broadcastModuleSnapshot() {
        NetworkManager.broadcastPacket(createModuleListPacket())
    }

    private fun createModuleListPacket(): ModuleListPacket {
        return ModuleListPacket().apply {
            modules = ModuleManager.modules.values.map { module ->
                ModuleListPacket.ModuleEntry(
                    moduleId = module.identity,
                    category = module.category.name,
                    enabled = module.enabled,
                    values = module.values
                        .filter { it.isDisplayable() }
                        .map(::createModuleValueEntry)
                        .toMutableList()
                )
            }.toMutableList()
        }
    }

    private fun createModuleValueEntry(value: Value<*>): ModuleListPacket.ModuleValueEntry {
        return when (value) {
            is OptionValue -> ModuleListPacket.ModuleValueEntry(
                valueId = value.getIdentity(),
                type = ModuleValueType.BOOLEAN,
                booleanValue = value.getValue()
            )

            is NumberValue -> ModuleListPacket.ModuleValueEntry(
                valueId = value.getIdentity(),
                type = ModuleValueType.NUMBER,
                numberValue = value.getValue(),
                minimum = value.minimum,
                maximum = value.maximum,
                increment = value.increment,
                unit = value.unit
            )

            is StringValue -> ModuleListPacket.ModuleValueEntry(
                valueId = value.getIdentity(),
                type = ModuleValueType.STRING,
                stringValue = value.getValue()
            )

            else -> throw IllegalStateException("Unsupported value type: ${value::class.qualifiedName}")
        }
    }
}
