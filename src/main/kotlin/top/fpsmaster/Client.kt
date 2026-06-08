package top.fpsmaster

import io.github.vlouboos.standaloneevent.api.ApiProvider
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.mcef.MCEFDownloadManager
import net.ccbluex.liquidbounce.mcef.MCEFPlatform
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import top.fpsmaster.command.CommandManager
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.hud.HudManager
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.translation.Language
import top.fpsmaster.web.BasicBrowser
import top.fpsmaster.web.api.LocalServer
import top.fpsmaster.web.cef.LoadHandler
import top.fpsmaster.web.network.packets.PacketRegistryInitializer


class Client : ModInitializer {
    override fun onInitialize() {
        // 初始化数据包注册
        logger.info("Initializing FPSMaster...")

        ApiProvider.injectApi()
        @Suppress("UnstableApiUsage") StandaloneEventAPI.getApi().makeDuplicatable() // We take it serious
        PacketRegistryInitializer.initialize()
        CommandManager.initialize()
        ModuleManager.initialize()
        ConfigManager.loadDefault()
        HudManager.initialize()
        logger.info("FPSMaster initialized successfully!")
        // 启动本地HTTP与WebSocket服务器
        try {
            LocalServer().start()
            logger.info("Local servers started")
        } catch (e: Exception) {
            logger.error("Failed to start local servers", e)
        }
        Language.initialize()
        // 注册客户端Tick事件
        ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { _: Minecraft? ->
            run {
                onTick()
                StandaloneEventAPI.getApi().call(TickEvent())
            }
        })
    }


    private fun initCefSafely(): Boolean {
        if (cefInitAttempted) {
            return cefReady
        }

        cefInitAttempted = true
        try {
            val newResourceManager = MCEF.INSTANCE.newResourceManager()
            if (!hasLocalJcefResources(newResourceManager) && newResourceManager.requiresDownload()) {
                newResourceManager.downloadJcef()
            }

            cefReady = MCEF.INSTANCE.initialize()
            if (cefReady) {
                MCEF.INSTANCE.client.addLoadHandler(LoadHandler())
            }
        } catch (exception: Throwable) {
            cefReady = false
            cefFailureMessage = createCefFailureMessage(exception)
            logger.error("Failed to initialize CEF", exception)
        }
        return cefReady
    }

    private fun hasLocalJcefResources(resourceManager: MCEFDownloadManager): Boolean {
        val platformDirectory = resourceManager.platformDirectory
        val missingLibraries = MCEFPlatform.getPlatform().requiredLibraries()
            .filterNot { platformDirectory.resolve(it).isFile }

        if (missingLibraries.isEmpty()) {
            logger.info("Using local JCEF resources at {}", platformDirectory.absolutePath)
            return true
        }

        logger.info("Local JCEF resources missing: {}", missingLibraries.joinToString(", "))
        return false
    }

    private fun createCefFailureMessage(exception: Throwable): String {
        val javaVersion = Runtime.version().feature()
        if (javaVersion > 21) {
            return "CEF 初始化失败：当前 Java $javaVersion，请使用 Java 21 运行客户端"
        }

        val rootCause = generateSequence(exception) { it.cause }.last()
        return "CEF 初始化失败：${rootCause.message ?: rootCause::class.java.simpleName}"
    }

    private fun onTick() {
        if (Minecraft.getInstance().screen == null) {
            if (GLFW.glfwGetKey(Minecraft.getInstance().window.handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
                initCefSafely()
                Minecraft.getInstance().setScreen(BasicBrowser())
            }
        }
    }

    companion object {
        private var cefInitAttempted = false
        var cefReady = false
            private set
        var cefFailureMessage: String? = null
            private set
    }
}
