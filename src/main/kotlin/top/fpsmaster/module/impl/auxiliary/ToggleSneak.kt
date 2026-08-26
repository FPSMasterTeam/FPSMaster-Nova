package top.fpsmaster.module.impl.auxiliary

import io.github.vlouboos.standaloneevent.api.EventHandler
import org.lwjgl.glfw.GLFW
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.mc
import top.fpsmaster.screenCompat
//? if >=1.21.5 {
import top.fpsmaster.mixin.interfaces.IKeyMapping
//?}
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

/**
 * Turns the sneak key into a toggle: one press stays crouched until the next press.
 *
 * Mirrors [Sprint]: the vanilla sneak binding is the trigger, so there is no second key to configure
 * and the game's own rebinding keeps working.
 */
class ToggleSneak : Module("toggle-sneak", Category.AUXILIARY) {
    init {
        values.add(toggleSneak)
    }

    @EventHandler
    fun onTick(@Suppress("unused") e: TickEvent) {
        if (!toggleSneak.getValue()) {
            releaseToggle()
            return
        }
        if (sneaking) {
            mc.options.keyShift.isDown = true
        }
    }

    @EventHandler
    fun onKey(event: KeyEvent) {
        if (!toggleSneak.getValue() || event.key.value != sneakKey() || mc.screenCompat != null) {
            return
        }

        sneaking = !sneaking
        if (!sneaking) {
            releaseToggle()
        }
    }

    override fun onDisable() {
        releaseToggle()
    }

    companion object {
        val toggleSneak = OptionValue("toggle-sneak", true)
        private var sneaking = false

        @JvmStatic
        fun isToggled(): Boolean = sneaking

        private fun releaseToggle() {
            sneaking = false
            // Hand the key back to whatever the player is physically holding.
            //? if >=1.21.11 {
            mc.options.keyShift.isDown = GLFW.glfwGetKey(mc.window.handle(), sneakKey()) == GLFW.GLFW_PRESS
            //?} else {
            /*mc.options.keyShift.isDown = GLFW.glfwGetKey(mc.window.window, sneakKey()) == GLFW.GLFW_PRESS
            *///?}
            mc.player?.isShiftKeyDown = false
        }

        private fun sneakKey(): Int {
            //? if >=1.21.5 {
            return (mc.options.keyShift as IKeyMapping).key.value
            //?} else {
            /*return mc.options.keyShift.key.value
            *///?}
        }
    }
}
