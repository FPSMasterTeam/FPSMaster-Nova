package top.fpsmaster.web.cef

import top.fpsmaster.mc
//? if >=1.21.5 {
import org.lwjgl.glfw.GLFW
//?}

/**
 * Optional in-game IME candidate-window positioning (see docs/ime-support.md).
 *
 * Uses GLFW's preedit API (glfwSetInputMode(GLFW_IME, …) + glfwSetPreeditCursorRectangle), which
 * only exists on LWJGL >= 3.3.4. The >=1.21.5 build forces lwjgl-glfw to 3.3.4 (build.gradle.kts);
 * on 1.20.1 (LWJGL 3.3.2) the API is absent, so every method here compiles to a no-op via Stonecutter.
 *
 * Coordinate space: glfwSetPreeditCursorRectangle takes window content-area coords (logical points),
 * the same space glfwGetCursorPos returns. positionAtCursor anchors at the raw cursor (used by the
 * web UI on click). positionAtGui converts MC GUI-scaled coords -> content coords for vanilla widgets.
 */
object ImeSupport {
    //? if >=1.21.5 {
    // The MC window is the current GL context on the render/main thread (where these are called),
    // so glfwGetCurrentContext() returns its handle without depending on the Window accessor, which
    // has shifted across the recent blaze3d rewrites.
    private val handle: Long get() = GLFW.glfwGetCurrentContext()
    //?}

    /** Enable/disable OS IME processing for the game window while a text input is focused. */
    fun setEnabled(enabled: Boolean) {
        //? if >=1.21.5 {
        runCatching {
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_IME, if (enabled) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
        }
        //?}
    }

    /** Anchor the candidate window at the current GLFW cursor position (already in content coords). */
    fun positionAtCursor() {
        //? if >=1.21.5 {
        runCatching {
            val h = handle
            val x = DoubleArray(1)
            val y = DoubleArray(1)
            GLFW.glfwGetCursorPos(h, x, y)
            GLFW.glfwSetPreeditCursorRectangle(h, x[0].toInt(), y[0].toInt(), 1, 18)
        }
        //?}
    }

    /**
     * Anchor the candidate window at a point given in MC GUI-scaled coordinates (e.g. a vanilla
     * EditBox caret). Converts GUI coords -> window content coords via the window's logical size.
     */
    fun positionAtGui(guiX: Double, guiY: Double, height: Int) {
        //? if >=1.21.5 {
        runCatching {
            val h = handle
            val ww = IntArray(1)
            val wh = IntArray(1)
            GLFW.glfwGetWindowSize(h, ww, wh)
            val gsw = mc.window.guiScaledWidth.toDouble().coerceAtLeast(1.0)
            val gsh = mc.window.guiScaledHeight.toDouble().coerceAtLeast(1.0)
            val cx = (guiX * ww[0] / gsw).toInt()
            val cy = (guiY * wh[0] / gsh).toInt()
            GLFW.glfwSetPreeditCursorRectangle(h, cx, cy, 1, height.coerceAtLeast(1))
        }
        //?}
    }

    fun reset() {
        //? if >=1.21.5 {
        runCatching { GLFW.glfwResetPreeditText(handle) }
        //?}
    }
}
