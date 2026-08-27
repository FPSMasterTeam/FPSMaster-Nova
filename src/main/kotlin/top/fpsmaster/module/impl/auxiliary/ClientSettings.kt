package top.fpsmaster.module.impl.auxiliary

import org.lwjgl.glfw.GLFW
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.KeyValue
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
                clickGuiKey,
                interfaceAnimations,
                hardwareAcceleration
            )
        )
    }

    companion object {
        private val scaleValues = doubleArrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0)

        val language = ChoiceValue("language", listOf("english", "chinese"), defaultLanguage())
        val blur = OptionValue("blur", false)
        val fixedScaleEnabled = OptionValue("fixed-scale-enabled", true)
        val fixedScale = ChoiceValue(
            "fixed-scale",
            listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "2.5x", "3x"),
            "1x"
        )
        val webViewScale = NumberValue("webview-scale", 100.0, 50.0, 150.0, 5.0, "%")
        val theme = ChoiceValue("theme", listOf("dark", "light"))
        val zoomBind = KeyValue("zoom-bind", GLFW.GLFW_KEY_LEFT_CONTROL)
        val clientCommand = OptionValue("client-command", true)
        val commandPrefix = StringValue(
            "command-prefix",
            ".",
            validator = { it.isNotBlank() && it.length <= 8 }
        )
        val clickGuiKey = KeyValue("click-gui-key", GLFW.GLFW_KEY_RIGHT_SHIFT)
        val interfaceAnimations = OptionValue("interface-animations", true)

        // Default off (opt-in), matching upstream CCBlueX/mcef, which ships accelerated paint as beta.
        // The zero-copy path is the fast one but the less-tested one, so users who want it turn it on.
        // Shown only where zero-copy can work at all (Windows/Linux on a supported GPU, 1.21.5+); on
        // other setups the switch is hidden and the browser stays on the CPU path whatever is stored,
        // since BasicBrowser gates on isAccelerationAvailable() as well.
        val hardwareAcceleration = OptionValue("hardware-acceleration", false) {
            top.fpsmaster.web.BasicBrowser.isAccelerationAvailable()
        }

        @JvmStatic
        fun uiScaleMultiplier(): Double {
            return scaleValues[fixedScale.index]
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
        fun lightTheme(): Boolean = theme.isSelected("light")

        @JvmStatic
        fun isZoomBindDown(): Boolean {
            val key = zoomBind.getValue()
            //? if >=1.21.11 {
            return key != 0 && GLFW.glfwGetKey(top.fpsmaster.mc.window.handle(), key) == GLFW.GLFW_PRESS
            //?} else {
            /*return key != 0 && GLFW.glfwGetKey(top.fpsmaster.mc.window.window, key) == GLFW.GLFW_PRESS
            *///?}
        }

        private fun defaultLanguage(): String {
            return if (java.util.Locale.getDefault().language.equals("zh", ignoreCase = true)) "chinese" else "english"
        }
    }
}
