package top.fpsmaster.module.impl.auxiliary

import io.github.vlouboos.standaloneevent.api.EventHandler
import org.lwjgl.glfw.GLFW
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

/**
 * Reads the mouse without the desktop's pointer acceleration and scaling applied.
 *
 * GLFW exposes this directly (`GLFW_RAW_MOUSE_MOTION`), and it only takes effect while the cursor is
 * captured — which is exactly when aiming happens. Platforms without raw motion (Wayland, some X11
 * setups) report it unsupported; there the module reports that instead of silently doing nothing.
 */
class RawInput : Module("raw-input", Category.AUXILIARY) {
    override fun onEnable() {
        if (!GLFW.glfwRawMouseMotionSupported()) {
            supported = false
            logger.warn("Raw mouse motion is not supported on this platform; raw input stays off")
            return
        }
        supported = true
        apply(true)
    }

    override fun onDisable() {
        if (supported) {
            apply(false)
        }
    }

    /**
     * Grabbing the cursor resets the input mode, so the flag is re-asserted while the game has the
     * mouse. Setting an input mode to the value it already holds is a no-op in GLFW.
     */
    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        if (supported && mc.mouseHandler.isMouseGrabbed) {
            apply(true)
        }
    }

    companion object {
        @Volatile
        private var supported = false

        @JvmStatic
        fun isSupported(): Boolean = supported

        private fun apply(enabled: Boolean) {
            val value = if (enabled) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE
            //? if >=1.21.11 {
            GLFW.glfwSetInputMode(mc.window.handle(), GLFW.GLFW_RAW_MOUSE_MOTION, value)
            //?} else {
            /*GLFW.glfwSetInputMode(mc.window.window, GLFW.GLFW_RAW_MOUSE_MOTION, value)
            *///?}
        }
    }
}
