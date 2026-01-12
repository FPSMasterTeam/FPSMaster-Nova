package top.fpsmaster

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW


class Client : ModInitializer {

    override fun onInitialize() {
        ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { client: Minecraft? -> onTick() })

    }


    private fun onTick() {
        if (Minecraft.getInstance().screen == null)
            if (GLFW.glfwGetKey(Minecraft.getInstance().window.handle(), GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS) {
                var guiScreen = BasicBrowser()
                guiScreen.initBrowser()
                Minecraft.getInstance().setScreen(guiScreen)
            }
    }

}
