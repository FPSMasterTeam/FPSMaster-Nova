package top.fpsmaster.web.cef

//? if >=1.21.5 {
import org.lwjgl.glfw.GLFW
//?}

/**
 * Optional in-game IME candidate-window positioning (see docs/ime-support.md).
 *
 * Uses GLFW's preedit API (glfwSetInputMode(GLFW_IME, …) + glfwSetPreeditCursorRectangle), which
 * only exists on LWJGL >= 3.3.4. The >=1.21.5 build forces LWJGL to 3.3.4 (build.gradle.kts); on
 * 1.20.1 (LWJGL 3.3.2) the API is absent, so every method here compiles to a no-op via Stonecutter.
 *
 * Positioning anchors the candidate box at the current GLFW cursor position. That value is already
 * in the window content-area coordinate space that glfwSetPreeditCursorRectangle expects, so no
 * GUI-scale / retina conversion is needed — when the user clicks into a web input the box appears
 * right there. (A precise per-input caret rect via JS getBoundingClientRect is a later refinement.)
 */
object ImeSupport {
    //? if >=1.21.5 {
    // The MC window is the current GL context on the render/main thread (where these are called),
    // so glfwGetCurrentContext() returns its handle without depending on the Window accessor, which
    // has shifted across the recent blaze3d rewrites.
    private val handle: Long get() = GLFW.glfwGetCurrentContext()
    //?}

    /** Enable/disable OS IME processing for the game window while a CEF input is focused. */
    fun setEnabled(enabled: Boolean) {
        //? if >=1.21.5 {
        runCatching {
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_IME, if (enabled) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
        }
        //?}
    }

    /** Move the IME candidate window to the current cursor position (content-area coords). */
    fun positionAtCursor() {
        //? if >=1.21.5 {
        runCatching {
            val x = DoubleArray(1)
            val y = DoubleArray(1)
            GLFW.glfwGetCursorPos(handle, x, y)
            GLFW.glfwSetPreeditCursorRectangle(handle, x[0].toInt(), y[0].toInt(), 1, 18)
        }
        //?}
    }

    fun reset() {
        //? if >=1.21.5 {
        runCatching { GLFW.glfwResetPreeditText(handle) }
        //?}
    }
}
