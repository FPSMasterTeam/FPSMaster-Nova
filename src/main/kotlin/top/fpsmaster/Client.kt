package top.fpsmaster

import io.github.vlouboos.standaloneevent.api.ApiProvider
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
import top.fpsmaster.cosmetic.CosmeticManager
import top.fpsmaster.hud.HudManager
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.musicui.MusicController
import top.fpsmaster.runtime.RuntimeProbe
import top.fpsmaster.shortcut.ShortcutManager
import top.fpsmaster.telemetry.TelemetryReporter
import top.fpsmaster.translation.Language

import top.fpsmaster.web.BasicBrowser
import top.fpsmaster.web.cef.ClientBrowser
import top.fpsmaster.web.cef.LoadHandler


class Client : ModInitializer {
    override fun onInitialize() {
        INSTANCE = this
        logger.info("Initializing FPSMaster...")

        AuthService.initialize()
        ApiProvider.injectApi(false) // We take it serious
        CommandManager.initialize()
        ModuleManager.initialize()
        ShortcutManager.initialize()
        HudManager.initialize()
        ConfigManager.loadDefault()
        CosmeticManager.initialize()
        logger.info("FPSMaster initialized successfully!")
        Language.initialize()
    }


    private var hostConfigured = false

    private fun configureHost() {
        if (hostConfigured) {
            return
        }
        hostConfigured = true
        // Legacy immediate-mode (<1.21.5) draws the imported zero-copy accel texture through a plain
        // position_tex shader, so have mcef R/B-swizzle imported accel textures (CEF ships BGRA imported
        // as RGBA8) at import time. 1.21.5+ swaps R/B in a dedicated shader instead and leaves this off.
        //? if <1.21.5 {
        /*net.ccbluex.liquidbounce.mcef.cef.MCEFRenderer.accelSwizzleBgra = true
        *///?}
        // mcef-nova is Minecraft-agnostic; supply the host bridge it needs (the per-version glue).
        MCEF.INSTANCE.setHost(object : MCEFHost {
            override fun schedule(task: Runnable) = Minecraft.getInstance().execute {
                //? if >=1.21.5 {
                // mcef tasks do raw GL (browser frame uploads) that bypasses GlStateManager's
                // caches; realign cache with GL reality right after each one, or the next glyph
                // baked into the font atlas goes through a stale bind and vanishes (p/q bug).
                try {
                    task.run()
                } finally {
                    top.fpsmaster.web.cef.ExternalGlStateSync.resync()
                }
                //?} else {
                /*task.run()
                *///?}
            }
            //? if >=1.21.11 {
            override fun windowHandle(): Long = Minecraft.getInstance().window.handle()
            //?} else {
            /*override fun windowHandle(): Long = Minecraft.getInstance().window.window
            *///?}
            override fun stopGame() = Minecraft.getInstance().stop()
        })
    }

    /** Checks for an existing local CEF runtime without downloading it. */
    private fun beginCefLoadInternal() {
        if (cefState != CefState.IDLE) {
            return
        }
        try {
            configureHost()
            val resourceManager = MCEF.INSTANCE.newResourceManager()
            if (hasLocalJcefResources(resourceManager) || !resourceManager.requiresDownload()) {
                cefState = CefState.DOWNLOADED
                return
            }
            cefFailureMessage = "CEF 运行库未安装；WebView 不会自动下载"
            cefState = CefState.FAILED
        } catch (exception: Throwable) {
            cefFailureMessage = createCefFailureMessage(exception)
            cefState = CefState.FAILED
            logger.error("Failed to begin CEF load", exception)
        }
    }

    /**
     * Render-thread: initialize CEF once the native bundle is present. Returns true when ready. Must run
     * on the render thread (touches GL). No-op until the local runtime check has finished.
     */
    private fun pumpCefInitInternal(): Boolean {
        if (cefState == CefState.READY) {
            return true
        }
        if (cefState != CefState.DOWNLOADED) {
            return false
        }
        try {
            cefReady = MCEF.INSTANCE.initialize()
            if (cefReady) {
                MCEF.INSTANCE.client.addLoadHandler(LoadHandler())
                cefState = CefState.READY
            } else {
                cefState = CefState.FAILED
            }
        } catch (exception: Throwable) {
            cefReady = false
            cefFailureMessage = createCefFailureMessage(exception)
            cefState = CefState.FAILED
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
        if (Minecraft.getInstance().screenCompat == null) {
            //? if >=1.21.11 {
            if (GLFW.glfwGetKey(Minecraft.getInstance().window.handle(), ClientSettings.clickGuiKey.getValue().toInt()) == GLFW.GLFW_PRESS) {
            //?} else {
            /*if (GLFW.glfwGetKey(Minecraft.getInstance().window.window, ClientSettings.clickGuiKey.getValue().toInt()) == GLFW.GLFW_PRESS) {*/
            //?}
                openClickGui()
            }
        }
    }

    enum class CefState { IDLE, DOWNLOADED, READY, FAILED }

    companion object {
        private var INSTANCE: Client? = null

        /** Client version, read from the mod metadata (fabric.mod.json version = gradle `mod_version`). */
        @JvmField
        val VERSION: String = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("fpsmaster")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        var cefReady = false
            private set
        var cefFailureMessage: String? = null
            private set

        @Volatile
        var cefState: CefState = CefState.IDLE
            private set

        @JvmStatic
        fun isCefReady(): Boolean = cefReady

        /** Checks for a local CEF runtime without downloading it. */
        @JvmStatic
        fun beginCefLoad() {
            INSTANCE?.beginCefLoadInternal()
        }

        /** Render-thread: initialize CEF once its local bundle is available. */
        @JvmStatic
        fun pumpCefInit(): Boolean = INSTANCE?.pumpCefInitInternal() ?: false

        @JvmStatic
        fun tick() {
            val client = INSTANCE ?: return
            client.onTick()
            TelemetryReporter.tick(System.currentTimeMillis())
            RuntimeProbe.frame()
        }

        /**
         * Runs at Minecraft.stop, on the render thread. Order matters: every browser has to be gone
         * before CEF itself is shut down, and the GPU-owning managers release while a GL context is
         * still current.
         */
        @JvmStatic
        fun shutdown() {
            if (INSTANCE == null) {
                return
            }
            TelemetryReporter.shutdown()
            MusicController.shutdown()
            CosmeticManager.shutdown()
            ClientBrowser.closeAll()
            try {
                MCEF.INSTANCE.shutdown()
            } catch (throwable: Throwable) {
                logger.warn("Failed to shut down CEF", throwable)
            }
            RuntimeProbe.report("shutdown")
        }

        @JvmStatic
        fun openClickGui() {
            Minecraft.getInstance().setScreenCompat(top.fpsmaster.ui.NativeClickGuiScreen())
        }

        @JvmStatic
        fun openOobe() {
            Minecraft.getInstance().setScreenCompat(top.fpsmaster.ui.NativeOobeScreen())
        }

        @JvmStatic
        fun openMainMenu(): Boolean {
            Minecraft.getInstance().setScreenCompat(top.fpsmaster.ui.NativeMainMenuScreen())
            return true
        }

        private fun resetCefForRetry() {
            if (cefState == CefState.FAILED) {
                cefState = CefState.IDLE
                cefReady = false
                cefFailureMessage = null
            }
        }

        /** Opens a standalone WebView only when a caller explicitly supplies a URL. */
        @JvmStatic
        fun openWebView(url: String) {
            if (cefState == CefState.READY && cefReady) {
                Minecraft.getInstance().setScreenCompat(BasicBrowser(url))
                return
            }
            resetCefForRetry()
            beginCefLoad()
            Minecraft.getInstance().setScreenCompat(top.fpsmaster.ui.CefLoadingScreen { BasicBrowser(url) })
        }
    }
}
