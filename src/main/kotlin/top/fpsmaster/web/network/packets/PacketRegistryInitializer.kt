package top.fpsmaster.web.network.packets

import net.minecraft.client.Minecraft
import top.fpsmaster.auth.AuthService
import top.fpsmaster.auth.FPSMasterApiClient
import top.fpsmaster.auth.UserInfo
import top.fpsmaster.command.CommandManager
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.logger
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.value.Value
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.translation.Language
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.telemetry.TelemetryReporter
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
        PacketRegistry.registerPacket { ClientConfigRequestPacket() }
        PacketRegistry.registerPacket { ClientConfigPacket() }
        PacketRegistry.registerPacket { ClientConfigUpdatePacket() }
        PacketRegistry.registerPacket { AccountStatusRequestPacket() }
        PacketRegistry.registerPacket { AccountStatusPacket() }
        PacketRegistry.registerPacket { AccountLoginPacket() }
        PacketRegistry.registerPacket { AccountLogoutPacket() }
        PacketRegistry.registerPacket { ConfigProfilesRequestPacket() }
        PacketRegistry.registerPacket { ConfigProfilesPacket() }
        PacketRegistry.registerPacket { ConfigProfileActionPacket() }
    }

    /**
     * 注册所有数据包处理器
     */
    private fun registerHandlers() {
        // 握手处理器
        PacketProcessor.registerHandler<HandshakePacket> { packet, context ->
            logger.info("Received handshake: ${packet.clientVersion}")
            context.channelHandlerContext?.let { channelContext ->
                NetworkManager.sendPacket(channelContext, HandshakeResponsePacket().apply {
                    success = packet.protocolVersion == 1
                    message = if (success) "OK" else "Unsupported protocol: ${packet.protocolVersion}"
                    serverVersion = Minecraft.getInstance().launchedVersion
                })
            }
        }

        // 心跳处理器
        PacketProcessor.registerHandler<HeartbeatPacket> { packet, context ->
            logger.debug("Received heartbeat at ${packet.timestamp}")
            // 可以回复心跳
        }

        // 命令执行处理器
        PacketProcessor.registerHandler<ExecuteCommandPacket> { packet, context ->
            logger.info("Executing command: ${packet.command}")
            val command = packet.command.trim().removePrefix(CommandManager.getPrefix())
            Minecraft.getInstance().execute {
                CommandManager.parse(command)
            }
            context.channelHandlerContext?.let { channelContext ->
                NetworkManager.sendPacket(channelContext, CommandResponsePacket().apply {
                    success = true
                    response = "Command queued"
                    error = null
                })
            }
        }

        // 玩家信息请求处理器
        PacketProcessor.registerHandler<PlayerInfoRequestPacket> { _, context ->
            logger.debug("Player info requested")
            val minecraft = Minecraft.getInstance()
            val player = minecraft.player
            context.channelHandlerContext?.let { channelContext ->
                NetworkManager.sendPacket(channelContext, PlayerInfoPacket().apply {
                    if (player != null) {
                        playerName = player.name.string
                        health = player.health
                        food = player.foodData.foodLevel
                        position = PlayerInfoPacket.Position(
                            x = player.x,
                            y = player.y,
                            z = player.z,
                            yaw = player.yRot,
                            pitch = player.xRot
                        )
                        //? if >=1.21.5 {
                        dimension = player.level().dimension().identifier().toString()
                        //?} else {
                        /*dimension = player.level().dimension().location().toString()*/
                        //?}
                    }
                })
            }
        }

        // UI事件处理器
        PacketProcessor.registerHandler<UIEventPacket> { packet, _ ->
            logger.info("Received UI event: ${packet.eventType}")
            if (packet.eventType.equals("refresh", ignoreCase = true)) {
                broadcastModuleSnapshot()
            }
        }

        // GUI加载事件处理器（从UI接收）
        PacketProcessor.registerHandler<GuiLoadEventPacket> { packet, context ->
            logger.info("Received GUI load event: ${packet.eventType}")
            // 事件仅用于触发UI动画，ACK由前端回传
        }

        // GUI加载ACK处理器（从服务器接收）
        PacketProcessor.registerHandler<GuiLoadAckPacket> { packet, _ ->
            logger.info("Received GUI load ACK: ${packet.message}")
            // 将ACK传递给当前的BasicBrowser实例
            Minecraft.getInstance().execute {
                BasicBrowser.handleAck(packet)
            }
        }

        PacketProcessor.registerHandler<ModuleListRequestPacket> { _, context ->
            logger.info("Received module list request")
            context.channelHandlerContext?.let { channelContext ->
                NetworkManager.sendPacket(channelContext, createModuleListPacket())
                NetworkManager.sendPacket(channelContext, createClientConfigPacket())
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
            ConfigManager.saveActive()
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
            ConfigManager.saveActive()
            broadcastModuleSnapshot()
        }

        PacketProcessor.registerHandler<ClientConfigRequestPacket> { _, context ->
            context.channelHandlerContext?.let { channelContext ->
                NetworkManager.sendPacket(channelContext, createClientConfigPacket())
            }
        }

        PacketProcessor.registerHandler<ClientConfigUpdatePacket> { packet, _ ->
            if (packet.updateMusicVolume) {
                ConfigManager.setMusicVolume(packet.musicVolume)
            }
            if (packet.updateAnonymousDataEnabled) {
                TelemetryReporter.setEnabled(packet.anonymousDataEnabled)
            }
            if (packet.updateClientPreferences) {
                ConfigManager.setClientPreferences(
                    background = packet.background,
                    oobeCompleted = packet.oobeCompleted,
                    antiCheatEnabled = packet.antiCheatEnabled,
                    classicBackgroundColor = packet.classicBackgroundColor,
                    classicBackgroundHue = packet.classicBackgroundHue,
                    classicBackgroundSaturation = packet.classicBackgroundSaturation,
                    classicBackgroundBrightness = packet.classicBackgroundBrightness,
                    classicBackgroundAlpha = packet.classicBackgroundAlpha,
                    classicBackgroundMode = packet.classicBackgroundMode
                )
            }
            ConfigManager.saveActive()
            broadcastClientConfig()
        }

        PacketProcessor.registerHandler<AccountStatusRequestPacket> { _, context ->
            val channelContext = context.channelHandlerContext ?: return@registerHandler
            if (!AuthService.isLoggedIn()) {
                NetworkManager.sendPacket(channelContext, createAccountStatusPacket(message = "未登录"))
                return@registerHandler
            }

            val cachedUser = FPSMasterApiClient.cachedUser()
            if (cachedUser != null) {
                NetworkManager.sendPacket(channelContext, createAccountStatusPacket(user = cachedUser))
            }

            FPSMasterApiClient.getUserInfo().whenComplete { result, exception ->
                val packet = if (exception != null) {
                    createAccountStatusPacket(
                        success = false,
                        loggedIn = AuthService.isLoggedIn(),
                        user = cachedUser,
                        message = exception.message ?: exception::class.simpleName.orEmpty()
                    )
                } else if (result.success && result.data != null) {
                    createAccountStatusPacket(user = result.data, message = result.message)
                } else {
                    createAccountStatusPacket(
                        success = false,
                        loggedIn = AuthService.isLoggedIn(),
                        user = cachedUser,
                        message = result.message
                    )
                }
                NetworkManager.sendPacket(channelContext, packet)
            }
        }

        PacketProcessor.registerHandler<AccountLoginPacket> { packet, context ->
            val channelContext = context.channelHandlerContext ?: return@registerHandler
            if (packet.usernameOrEmail.isBlank() || packet.password.isBlank()) {
                NetworkManager.sendPacket(
                    channelContext,
                    createAccountStatusPacket(success = false, message = "请输入账号和密码")
                )
                return@registerHandler
            }

            FPSMasterApiClient.login(packet.usernameOrEmail, packet.password).whenComplete { result, exception ->
                val statusPacket = if (exception != null) {
                    createAccountStatusPacket(success = false, message = exception.message ?: exception::class.simpleName.orEmpty())
                } else if (result.success) {
                    createAccountStatusPacket(user = result.data?.user?.toUserInfo(), message = result.message)
                } else {
                    createAccountStatusPacket(success = false, message = result.message)
                }
                NetworkManager.sendPacket(channelContext, statusPacket)
            }
        }

        PacketProcessor.registerHandler<AccountLogoutPacket> { _, context ->
            val channelContext = context.channelHandlerContext ?: return@registerHandler
            FPSMasterApiClient.logout().whenComplete { result, exception ->
                val message = exception?.message ?: result?.message ?: "已退出登录"
                NetworkManager.sendPacket(
                    channelContext,
                    createAccountStatusPacket(success = exception == null && (result?.success != false), message = message)
                )
            }
        }

        PacketProcessor.registerHandler<ConfigProfilesRequestPacket> { _, context ->
            context.channelHandlerContext?.let { channelContext ->
                NetworkManager.sendPacket(channelContext, createConfigProfilesPacket())
            }
        }

        PacketProcessor.registerHandler<ConfigProfileActionPacket> { packet, context ->
            val channelContext = context.channelHandlerContext ?: return@registerHandler
            val result = runCatching {
                when (packet.action.lowercase()) {
                    "create" -> ConfigManager.create(packet.name)
                    "save" -> {
                        if (packet.name.isBlank()) {
                            ConfigManager.saveActive()
                        } else {
                            ConfigManager.saveAs(packet.name)
                        }
                    }
                    "load" -> ConfigManager.loadProfile(packet.name)
                    "delete" -> ConfigManager.delete(packet.name)
                    "rename" -> ConfigManager.rename(packet.name, packet.targetName)
                    "import" -> ConfigManager.importProfile(packet.name)
                    "export" -> ConfigManager.exportActive(packet.name)
                    "alloff" -> ConfigManager.resetActiveToAllOff()
                    else -> error("Unsupported config action: ${packet.action}")
                }
            }

            val message = result.fold(
                onSuccess = { "OK" },
                onFailure = { it.message ?: it::class.simpleName.orEmpty() }
            )
            NetworkManager.sendPacket(
                channelContext,
                createConfigProfilesPacket(success = result.isSuccess, message = message)
            )
            if (result.isSuccess) {
                broadcastModuleSnapshot()
                broadcastClientConfig()
            }
        }
    }

    @JvmStatic
    fun broadcastModuleSnapshot() {
        NetworkManager.broadcastPacket(createModuleListPacket())
    }

    @JvmStatic
    fun broadcastClientConfig() {
        NetworkManager.broadcastPacket(createClientConfigPacket())
    }

    private fun createClientConfigPacket(): ClientConfigPacket {
        return ClientConfigPacket().apply {
            musicVolume = ConfigManager.musicVolume
            anonymousDataEnabled = TelemetryReporter.anonymousDataEnabled
            telemetryInstanceId = TelemetryReporter.telemetryInstanceId
            background = ConfigManager.background
            oobeCompleted = ConfigManager.oobeCompleted
            antiCheatEnabled = ConfigManager.antiCheatEnabled
            classicBackgroundColor = ConfigManager.classicBackgroundColor
            classicBackgroundHue = ConfigManager.classicBackgroundHue
            classicBackgroundSaturation = ConfigManager.classicBackgroundSaturation
            classicBackgroundBrightness = ConfigManager.classicBackgroundBrightness
            classicBackgroundAlpha = ConfigManager.classicBackgroundAlpha
            classicBackgroundMode = ConfigManager.classicBackgroundMode
        }
    }

    private fun createAccountStatusPacket(
        success: Boolean = true,
        loggedIn: Boolean = userLoggedIn(),
        user: UserInfo? = FPSMasterApiClient.cachedUser(),
        message: String = ""
    ): AccountStatusPacket {
        return AccountStatusPacket().apply {
            this.success = success
            this.loggedIn = loggedIn && AuthService.isLoggedIn()
            this.username = user?.username
            this.displayName = user?.displayName
            this.level = user?.level ?: 0
            this.message = message
        }
    }

    private fun userLoggedIn(): Boolean {
        return AuthService.isLoggedIn()
    }

    private fun createConfigProfilesPacket(success: Boolean = true, message: String = ""): ConfigProfilesPacket {
        return ConfigProfilesPacket().apply {
            this.success = success
            this.message = message
            this.activeProfile = ConfigManager.activeName()
            this.profiles = ConfigManager.listNames().toMutableList()
        }
    }

    private fun createModuleListPacket(): ModuleListPacket {
        return ModuleListPacket().apply {
            modules = ModuleManager.modules.values.map { module ->
                val moduleKey = langKey(module.identity)
                ModuleListPacket.ModuleEntry(
                    moduleId = module.identity,
                    category = module.category.name,
                    displayName = translate(moduleKey),
                    description = translate("$moduleKey.desc"),
                    enabled = module.enabled,
                    values = module.values
                        .filter { it.isDisplayable() }
                        .map { createModuleValueEntry(moduleKey, it) }
                        .toMutableList()
                )
            }.toMutableList()
        }
    }

    /** Normalises an identifier to the lang-key form: lowercase, alphanumeric only. */
    private fun langKey(identity: String): String =
        identity.lowercase().filter { it.isLetterOrDigit() }

    /** Returns the translation for [key], or an empty string when the key is missing
     *  (so the web UI can fall back to its own metadata / prettified id). */
    private fun translate(key: String): String {
        val value = Language.get(key)
        return if (value == key) "" else value
    }

    private fun createModuleValueEntry(moduleKey: String, value: Value<*>): ModuleListPacket.ModuleValueEntry {
        val entry = createModuleValueEntry(value)
        entry.label = translate("$moduleKey.${langKey(value.getIdentity())}")
        return entry
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
