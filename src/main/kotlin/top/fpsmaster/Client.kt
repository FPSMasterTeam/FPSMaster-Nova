package top.fpsmaster

import io.github.vlouboos.standaloneevent.api.ApiProvider
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.mcef.MCEFDownloadManager
import net.ccbluex.liquidbounce.mcef.MCEFHost
import net.ccbluex.liquidbounce.mcef.MCEFPlatform
import net.fabricmc.api.ModInitializer
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import top.fpsmaster.auth.AuthService
import top.fpsmaster.command.CommandManager
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.hud.HudManager
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.shortcut.ShortcutManager
import top.fpsmaster.telemetry.TelemetryReporter
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.NovaOobeScreen
import top.fpsmaster.web.BasicBrowser
import top.fpsmaster.web.api.LocalServer
import top.fpsmaster.web.cef.LoadHandler
import top.fpsmaster.web.network.packets.PacketRegistryInitializer


class Client : ModInitializer {
    override fun onInitialize() {
        INSTANCE = this
        // 初始化数据包注册
        logger.info("Initializing FPSMaster...")

        AuthService.initialize()
        ApiProvider.injectApi()
        @Suppress("UnstableApiUsage") StandaloneEventAPI.getApi().makeDuplicatable() // We take it serious
        PacketRegistryInitializer.initialize()
        CommandManager.initialize()
        ModuleManager.initialize()
        ShortcutManager.initialize()
        HudManager.initialize()
        ConfigManager.loadDefault()
        logger.info("FPSMaster initialized successfully!")
        // 启动本地HTTP与WebSocket服务器
        try {
            LocalServer().start()
            logger.info("Local servers started")
        } catch (e: Exception) {
            logger.error("Failed to start local servers", e)
        }
        Language.initialize()
    }


    private fun initCefSafely(): Boolean {
        if (cefInitAttempted) {
            return cefReady
        }

        cefInitAttempted = true
        try {
            // mcef-nova is Minecraft-agnostic; supply the host bridge it needs (the per-version glue).
            MCEF.INSTANCE.setHost(object : MCEFHost {
                override fun schedule(task: Runnable) = Minecraft.getInstance().execute(task)
                //? if >=1.21.11 {
                override fun windowHandle(): Long = Minecraft.getInstance().window.handle()
                //?} else {
                /*override fun windowHandle(): Long = Minecraft.getInstance().window.window*/
                //?}
                override fun stopGame() = Minecraft.getInstance().stop()
            })
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
            //? if >=1.21.11 {
            if (GLFW.glfwGetKey(Minecraft.getInstance().window.handle(), ClientSettings.clickGuiKey.getValue().toInt()) == GLFW.GLFW_PRESS) {
            //?} else {
            /*if (GLFW.glfwGetKey(Minecraft.getInstance().window.window, ClientSettings.clickGuiKey.getValue().toInt()) == GLFW.GLFW_PRESS) {*/
            //?}
                initCefSafely()
                Minecraft.getInstance().setScreen(BasicBrowser())
            }
        }
    }

    companion object {
        private var INSTANCE: Client? = null
        private var cefInitAttempted = false
        var cefReady = false
            private set
        var cefFailureMessage: String? = null
            private set

        @JvmStatic
        fun tick() {
            val client = INSTANCE ?: return
            client.onTick()
            TelemetryReporter.tick(System.currentTimeMillis())
            StandaloneEventAPI.getApi().call(TickEvent())
        }

        @JvmStatic
        fun shutdown() {
            if (INSTANCE != null) {
                TelemetryReporter.shutdown()
            }
        }

        @JvmStatic
        fun openClickGui() {
            INSTANCE?.initCefSafely()
            Minecraft.getInstance().setScreen(BasicBrowser())
        }

        @JvmStatic
        fun openOobe() {
            INSTANCE?.initCefSafely()
            Minecraft.getInstance().setScreen(NovaOobeScreen())
        }
    }
}
