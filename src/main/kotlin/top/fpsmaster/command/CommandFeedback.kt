package top.fpsmaster.command

import net.minecraft.network.chat.Component
import top.fpsmaster.mc

object CommandFeedback {
    private const val PREFIX = "\u00a78[\u00a7bFPSMaster\u00a78] "

    fun info(message: String) {
        push("\u00a77$message")
    }

    fun success(message: String) {
        push("\u00a7a$message")
    }

    fun error(message: String) {
        push("\u00a7c$message")
    }

    private fun push(message: String) {
        // 26.2 changed Gui's chat access; route client feedback through the player's message sink
        // (available in-game, which is where commands run).
        //? if >=26 {
        /*mc.player?.sendSystemMessage(Component.literal(PREFIX + message))
        *///?} else {
        mc.gui.chat.addMessage(Component.literal(PREFIX + message))
        //?}
    }
}
