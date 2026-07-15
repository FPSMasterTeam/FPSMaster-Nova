package top.fpsmaster

import io.github.vlouboos.standaloneevent.api.ApiProvider
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.mcef.MCEFDownloadManager
import net.ccbluex.liquidbounce.mcef.MCEFHost
import net.ccbluex.liquidbounce.mcef.MCEFPlatform
import net.ccbluex.liquidbounce.mcef.listeners.MCEFProgressListener
import top.fpsmaster.ui.CefLoadingScreen
import net.fabricmc.api.ModInitializer
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import top.fpsmaster.auth.AuthService
import top.fpsmaster.command.CommandManager
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.hud.HudManager
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.module.impl.render.ClickGUI
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
        ApiProvider.injectApi(false) // We take it serious
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


    private var hostConfigured = false

    private fun configureHost() {
        if (hostConfigured) {
            return
        }
        hostConfigured = true
        // GPU-accelerated (zero-copy) webview needs the CEF message loop pumped on the render
        // thread, with accelerated frames imported into Minecraft's own GL context — mcef's
        // dedicated CEF thread stalls CEF's accelerated OSR after a single frame. The pump mode is
        // fixed at CEF init, so decide it here from the persisted toggle; when acceleration is off
        // we keep the dedicated CEF thread (the render thread never blocks in the CEF pump).
        if (ClickGUI.hardwareAcceleration.getValue()) {
            System.setProperty("mcef.pumpOnRenderThread", "true")
        }
        // JCEF native bundles are served exclusively from our own mirror (mainland-friendly).
        MCEF.INSTANCE.settings.setHosts(listOf("https://oss2.fpsmaster.com"))
        // Legacy immediate-mode (<1.21.5) draws the imported zero-copy accel texture through a plain
        // position_tex shader, so have mcef R/B-swizzle imported accel textures (CEF ships BGRA imported
        // as RGBA8) at import time. 1.21.5+ swaps R/B in a dedicated shader instead and leaves this off.
        //? if <1.21.5 {
        /*net.ccbluex.liquidbounce.mcef.cef.MCEFRenderer.accelSwizzleBgra = true*/
        //?}
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
                /*task.run()*/
                //?}
            }
            //? if >=1.21.11 {
            override fun windowHandle(): Long = Minecraft.getInstance().window.handle()
            //?} else {
            /*override fun windowHandle(): Long = Minecraft.getInstance().window.window*/
            //?}
            override fun stopGame() = Minecraft.getInstance().stop()
        })
    }

    private fun initCefSafely(): Boolean {
        if (cefInitAttempted) {
            return cefReady
        }

        cefInitAttempted = true
        try {
            configureHost()
            val newResourceManager = MCEF.INSTANCE.newResourceManager()
            if (!hasLocalJcefResources(newResourceManager) && newResourceManager.requiresDownload()) {
                newResourceManager.downloadJcef()
            }

            cefReady = MCEF.INSTANCE.initialize()
            if (cefReady) {
                MCEF.INSTANCE.client.addLoadHandler(LoadHandler())
                cefState = CefState.READY
            }
        } catch (exception: Throwable) {
            cefReady = false
            cefFailureMessage = createCefFailureMessage(exception)
            cefState = CefState.FAILED
            logger.error("Failed to initialize CEF", exception)
        }
        return cefReady
    }

    /**
     * Non-blocking: configure the host and, when the JCEF native bundle is missing, download it on a
     * background thread (progress reported into [cefDownloadProgress]/[cefDownloadStage]). Safe to call
     * more than once; only the first call does anything.
     */
    private fun beginCefLoadInternal() {
        if (cefState != CefState.IDLE) {
            return
        }
        try {
            configureHost()
            val resourceManager = MCEF.INSTANCE.newResourceManager()
            if (hasLocalJcefResources(resourceManager) || !resourceManager.requiresDownload()) {
                cefState = CefState.DOWNLOADED
                cefDownloadProgress = 1f
                return
            }

            cefState = CefState.DOWNLOADING
            resourceManager.registerProgressListener(object : MCEFProgressListener {
                override fun onProgressUpdate(stage: String, progress: Float) {
                    cefDownloadStage = stage
                    cefDownloadProgress = normalizeProgress(progress)
                }

                override fun onComplete() {
                    cefDownloadProgress = 1f
                }

                override fun onFileStart(name: String) {
                    cefDownloadStage = name
                }

                override fun onFileProgress(name: String, done: Long, total: Long, indeterminate: Boolean) {
                    if (total > 0) {
                        cefDownloadProgress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }
                }

                override fun onFileEnd(name: String) {}
            })

            Thread({
                try {
                    resourceManager.downloadJcef()
                    cefDownloadProgress = 1f
                    cefState = CefState.DOWNLOADED
                } catch (exception: Throwable) {
                    cefFailureMessage = createCefFailureMessage(exception)
                    cefState = CefState.FAILED
                    logger.error("Failed to download CEF", exception)
                }
            }, "FPSMaster-CEF-Download").apply { isDaemon = true }.start()
        } catch (exception: Throwable) {
            cefFailureMessage = createCefFailureMessage(exception)
            cefState = CefState.FAILED
            logger.error("Failed to begin CEF load", exception)
        }
    }

    /**
     * Render-thread: initialize CEF once the native bundle is present. Returns true when ready. Must run
     * on the render thread (touches GL). No-op until the download has finished.
     */
    private fun pumpCefInitInternal(): Boolean {
        if (cefState == CefState.READY) {
            return true
        }
        if (cefState != CefState.DOWNLOADED) {
            return false
        }
        cefInitAttempted = true
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

    private fun normalizeProgress(progress: Float): Float {
        // Some stages report 0..1, others 0..100 — accept both.
        val fraction = if (progress > 1f) progress / 100f else progress
        return fraction.coerceIn(0f, 1f)
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
                initCefSafely()
                Minecraft.getInstance().setScreenCompat(BasicBrowser())
            }
        }
    }

    enum class CefState { IDLE, DOWNLOADING, DOWNLOADED, READY, FAILED }

    companion object {
        private var INSTANCE: Client? = null

        /** Client version, read from the mod metadata (fabric.mod.json version = gradle `mod_version`). */
        @JvmField
        val VERSION: String = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("fpsmaster")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        private var cefInitAttempted = false
        var cefReady = false
            private set
        var cefFailureMessage: String? = null
            private set

        @Volatile
        var cefState: CefState = CefState.IDLE
            private set

        @Volatile
        var cefDownloadProgress: Float = 0f
            private set

        @Volatile
        var cefDownloadStage: String = ""
            private set

        @JvmStatic
        fun isCefReady(): Boolean = cefReady

        @JvmStatic
        fun isCefFailed(): Boolean = cefState == CefState.FAILED

        /** Non-blocking: start (or continue) loading CEF — downloads the JCEF bundle off-thread. */
        @JvmStatic
        fun beginCefLoad() {
            INSTANCE?.beginCefLoadInternal()
        }

        /** Render-thread: initialize CEF once its bundle is downloaded. Returns true when ready. */
        @JvmStatic
        fun pumpCefInit(): Boolean = INSTANCE?.pumpCefInitInternal() ?: false

        /** Show the native loading screen while CEF downloads/initializes, then open [next]. */
        @JvmStatic
        fun showCefLoadingScreen(oobeCompleted: Boolean) {
            Minecraft.getInstance().setScreenCompat(
                CefLoadingScreen {
                    if (!oobeCompleted) NovaOobeScreen() else BasicBrowser(BasicBrowser.Mode.MAINMENU)
                }
            )
        }

        @JvmStatic
        fun tick() {
            val client = INSTANCE ?: return
            client.onTick()
            TelemetryReporter.tick(System.currentTimeMillis())
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
            Minecraft.getInstance().setScreenCompat(BasicBrowser())
        }

        @JvmStatic
        fun openOobe() {
            INSTANCE?.initCefSafely()
            Minecraft.getInstance().setScreenCompat(NovaOobeScreen())
        }

        /**
         * Opens the webview main menu (replacing the vanilla title screen). Returns false if CEF is not
         * usable, so the caller can fall back to the vanilla screen and never leave the player stuck.
         */
        @JvmStatic
        fun openMainMenu(): Boolean {
            INSTANCE?.initCefSafely()
            if (!cefReady) {
                return false
            }
            Minecraft.getInstance().setScreenCompat(BasicBrowser(BasicBrowser.Mode.MAINMENU))
            return true
        }
    }
}
