package top.fpsmaster.module.impl.auxiliary

import org.lwjgl.glfw.GLFW
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue

class ClientSettings : Module("client-settings", Category.AUXILIARY, canBeEnabled = false) {
    init {
        values.addAll(
            arrayOf(
                language,
                blur,
                fixedScaleEnabled,
                fixedScale,
                webViewScale,
                theme,
                zoomBind,
                clientCommand,
                commandPrefix,
                clickGuiKey
            )
        )
    }

    companion object {
        private val scaleValues = doubleArrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0)

        val language = NumberValue("language", defaultLanguage().toDouble(), 0.0, 1.0, 1.0)
        val blur = OptionValue("blur", false)
        val fixedScaleEnabled = OptionValue("fixed-scale-enabled", true)
        val fixedScale = NumberValue("fixed-scale", 2.0, 0.0, 7.0, 1.0)
        val webViewScale = NumberValue("webview-scale", 100.0, 50.0, 150.0, 5.0, "%")
        val theme = NumberValue("theme", 0.0, 0.0, 1.0, 1.0)
        val zoomBind = NumberValue(
            "zoom-bind",
            GLFW.GLFW_KEY_LEFT_CONTROL.toDouble(),
            0.0,
            512.0,
            1.0
        )
        val clientCommand = OptionValue("client-command", true)
        val commandPrefix = StringValue(
            "command-prefix",
            ".",
            validator = { it.isNotBlank() && it.length <= 8 }
        )
        val clickGuiKey = NumberValue(
            "click-gui-key",
            GLFW.GLFW_KEY_RIGHT_SHIFT.toDouble(),
            0.0,
            512.0,
            1.0
        )

        @JvmStatic
        fun uiScaleMultiplier(): Double {
            val index = fixedScale.getValue().toInt().coerceIn(scaleValues.indices)
            return scaleValues[index]
        }

        @JvmStatic
        fun hudRenderScale(): Float {
            if (fixedScaleEnabled.getValue()) {
                return uiScaleMultiplier().toFloat()
            }
            val vanillaScale = top.fpsmaster.mc.window.guiScale.toFloat().coerceAtLeast(1.0f)
            return (uiScaleMultiplier() / vanillaScale).toFloat()
        }

        @JvmStatic
        fun isZoomBindDown(): Boolean {
            val key = zoomBind.getValue().toInt()
            //? if >=1.21.11 {
            return key != 0 && GLFW.glfwGetKey(top.fpsmaster.mc.window.handle(), key) == GLFW.GLFW_PRESS
            //?} else {
            /*return key != 0 && GLFW.glfwGetKey(top.fpsmaster.mc.window.window, key) == GLFW.GLFW_PRESS
            *///?}
        }

        private fun defaultLanguage(): Int {
            return if (java.util.Locale.getDefault().language.equals("zh", ignoreCase = true)) 1 else 0
        }
    }
}
