package top.fpsmaster.module.impl.optimization

import io.github.vlouboos.standaloneevent.api.EventHandler
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import top.fpsmaster.FunctionUtil
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.module.Category
import top.fpsmaster.screenCompat
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.KeyValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SmoothZoom : Module("smooth-zoom", Category.OPTIMIZATION) {
    init {
        values.addAll(
            arrayOf(
                smoothCamera,
                zoomBind,
                smoothMouse,
                wheelZoom,
                speed
            )
        )
        enabled = true
    }

    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        val minecraft = Minecraft.getInstance()
        //? if >=1.21.11 {
        val shouldZoom = minecraft.screenCompat == null &&
                GLFW.glfwGetKey(minecraft.window.handle(), zoomBind.getValue()) == GLFW.GLFW_PRESS
        //?} else {
        /*val shouldZoom = minecraft.screen == null &&
            GLFW.glfwGetKey(minecraft.window.window, zoomBind.getValue()) == GLFW.GLFW_PRESS
        *///?}

        setZooming(shouldZoom)
        updateSmoothMouse(minecraft)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        val minecraft = Minecraft.getInstance()
        active = false
        setZooming(false)
        restoreSmoothMouse(minecraft)
        currentScale = 1.0f
        zoomScale = 4.0f
    }

    companion object {
        @JvmField
        val smoothCamera = OptionValue("smooth-camera", false)

        @JvmField
        val zoomBind = KeyValue("zoom-bind", GLFW.GLFW_KEY_C)

        @JvmField
        val smoothMouse = OptionValue("smooth-mouse", false)

        @JvmField
        val wheelZoom = OptionValue("wheel-zoom", false)

        @JvmField
        val speed = NumberValue("speed", 4.0, 0.1, 10.0, 0.1) { smoothCamera.getValue() }

        private var active = false
        private var zooming = false
        private var previousSmoothCamera: Boolean? = null
        private var currentScale = 1.0f
        private var targetScale = 1.0f
        private var animation = FunctionUtil.done(1.0f)

        @JvmField
        var zoomScale = 4.0f

        @JvmStatic
        fun isZooming(): Boolean = active && zooming

        @JvmStatic
        fun modifyFov(fov: Float): Float {
            if (!active) {
                return fov
            }

            val scale = if (zooming) 1f / zoomScale else 1f
            currentScale = if (smoothCamera.getValue()) {
                if (scale != targetScale) {
                    targetScale = scale
                    animation = FunctionUtil.cubicBezier(
                        currentScale,
                        targetScale,
                        (1000 / speed.getValue()).toLong(),
                        0.16f,
                        1f,
                        0.3f,
                        1f
                    )
                }
                animation.calculate()
            } else {
                scale
            }
            return currentScale * fov
        }

        @JvmStatic
        fun handleScroll(yOffset: Double): Boolean {
            if (!active || !zooming || !wheelZoom.getValue()) {
                return false
            }

            zoomScale = min(20.0f, max(2.0f, zoomScale + yOffset.toFloat()))
            return true
        }

        private fun setZooming(value: Boolean) {
            zooming = value
            if (!value && !smoothCamera.getValue()) {
                currentScale = 1.0f
                zoomScale = 4.0f
            }
        }

        private fun updateSmoothMouse(minecraft: Minecraft) {
            if (!smoothMouse.getValue()) {
                restoreSmoothMouse(minecraft)
                return
            }

            if (zooming && previousSmoothCamera == null) {
                previousSmoothCamera = minecraft.options.smoothCamera
                minecraft.options.smoothCamera = true
            } else if (!zooming) {
                restoreSmoothMouse(minecraft)
            }
        }

        private fun restoreSmoothMouse(minecraft: Minecraft) {
            previousSmoothCamera?.let {
                minecraft.options.smoothCamera = it
                previousSmoothCamera = null
            }
        }
    }
}
