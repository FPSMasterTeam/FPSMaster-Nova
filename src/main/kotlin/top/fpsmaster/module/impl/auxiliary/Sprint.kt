package top.fpsmaster.module.impl.auxiliary

import io.github.vlouboos.standaloneevent.api.EventHandler
import org.lwjgl.glfw.GLFW
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.mc
//? if >=1.21.5 {
import top.fpsmaster.mixin.interfaces.IKeyMapping
//?}
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

class Sprint : Module("sprint", Category.AUXILIARY) {
    init {
        values.add(toggleSprint)
    }

    @EventHandler
    fun onTick(@Suppress("unused") e: TickEvent) {
        if (!toggleSprint.getValue()) {
            sprinting = true
        }
        mc.options.keySprint.isDown = sprinting
    }

    @EventHandler
    fun onKey(event: KeyEvent) {
        if (!toggleSprint.getValue() || event.key.value != sprintKey()) {
            return
        }

        sprinting = !sprinting
        if (!sprinting) {
            mc.player?.setSprinting(false)
        }
    }

    override fun onDisable() {
        //? if >=1.21.11 {
        mc.options.keySprint.isDown = GLFW.glfwGetKey(mc.window.handle(), sprintKey()) == GLFW.GLFW_PRESS
        //?} else {
        /*mc.options.keySprint.isDown = GLFW.glfwGetKey(mc.window.window, sprintKey()) == GLFW.GLFW_PRESS*/
        //?}
        mc.player?.setSprinting(false)
        sprinting = true
    }

    companion object {
        val toggleSprint = OptionValue("toggle-sprint", true)
        private var sprinting = true

        @JvmStatic
        fun isToggled(): Boolean = sprinting

        private fun sprintKey(): Int {
            //? if >=1.21.5 {
            return (mc.options.keySprint as IKeyMapping).key.value
            //?} else {
            /*return mc.options.keySprint.key.value*/
            //?}
        }
    }
}
