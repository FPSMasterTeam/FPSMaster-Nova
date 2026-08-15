package top.fpsmaster.module.impl.render

import io.github.vlouboos.standaloneevent.api.EventHandler
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.module.Category
import top.fpsmaster.screenCompat
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import kotlin.math.max
import kotlin.math.min

class FreeLook : Module("free-look", Category.RENDER) {
    init {
        values.add(bind)
    }

    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        val minecraft = Minecraft.getInstance()
        //? if >=1.21.11 {
        val shouldUse = minecraft.screenCompat == null &&
            GLFW.glfwGetKey(minecraft.window.handle(), bind.getValue().toInt()) == GLFW.GLFW_PRESS
        //?} else {
        /*val shouldUse = minecraft.screen == null &&
            GLFW.glfwGetKey(minecraft.window.window, bind.getValue().toInt()) == GLFW.GLFW_PRESS
        *///?}

        if (shouldUse && !activeCamera) {
            start(minecraft)
        } else if (!shouldUse && activeCamera) {
            stop(minecraft)
        }
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        stop(Minecraft.getInstance())
    }

    companion object {
        private var active = false
        private var activeCamera = false
        private var previousCameraType: CameraType? = null
        private var cameraYaw = 0.0f
        private var cameraPitch = 0.0f
        val bind = NumberValue("bind", GLFW.GLFW_KEY_LEFT_ALT.toDouble(), 0.0, 512.0, 1.0)

        @JvmStatic
        fun isCameraActive(): Boolean = active && activeCamera

        @JvmStatic
        fun cameraYaw(entityYaw: Float): Float = if (isCameraActive()) cameraYaw else entityYaw

        @JvmStatic
        fun cameraPitch(entityPitch: Float): Float = if (isCameraActive()) cameraPitch else entityPitch

        @JvmStatic
        fun handleTurn(yawDelta: Double, pitchDelta: Double): Boolean {
            if (!isCameraActive()) {
                return false
            }

            cameraYaw += yawDelta.toFloat() * 0.15f
            cameraPitch = min(90.0f, max(-90.0f, cameraPitch + pitchDelta.toFloat() * 0.15f))
            return true
        }

        private fun start(minecraft: Minecraft) {
            val player = minecraft.player ?: return
            previousCameraType = minecraft.options.cameraType
            cameraYaw = player.yRot
            cameraPitch = player.xRot
            minecraft.options.cameraType = CameraType.THIRD_PERSON_BACK
            activeCamera = true
        }

        private fun stop(minecraft: Minecraft) {
            if (!activeCamera) {
                return
            }

            previousCameraType?.let { minecraft.options.cameraType = it }
            previousCameraType = null
            activeCamera = false
        }
    }
}
