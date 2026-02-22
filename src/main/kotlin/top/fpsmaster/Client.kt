package top.fpsmaster

import io.github.vlouboos.standaloneevent.api.ApiProvider
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import net.ccbluex.liquidbounce.mcef.MCEF
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import top.fpsmaster.command.CommandManager
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.module.ModuleManager
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
        logger.info("FPSMaster initialized successfully!")
        // 启动本地HTTP与WebSocket服务器
        try {
            LocalServer().start()
            logger.info("Local servers started")
        } catch (e: Exception) {
            logger.error("Failed to start local servers", e)
        }
        initCef()
        // 注册客户端Tick事件
        ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { client: Minecraft? ->
            run {
                onTick()
                StandaloneEventAPI.getApi().call(TickEvent())
            }
        })
    }


    fun initCef(){
        val newResourceManager = MCEF.INSTANCE.newResourceManager()
        if (newResourceManager.requiresDownload())
            newResourceManager.downloadJcef()
        MCEF.INSTANCE.initialize()
        MCEF.INSTANCE.client.addLoadHandler(LoadHandler())
    }

    private fun onTick() {
        if (Minecraft.getInstance().screen == null)
            if (GLFW.glfwGetKey(Minecraft.getInstance().window.handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
                var guiScreen = BasicBrowser()
                guiScreen.initBrowser()
                Minecraft.getInstance().setScreen(guiScreen)
            }
    }

}
