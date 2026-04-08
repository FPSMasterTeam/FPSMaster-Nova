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
        mc.gui.chat.addMessage(Component.literal(PREFIX + message))
    }
}
