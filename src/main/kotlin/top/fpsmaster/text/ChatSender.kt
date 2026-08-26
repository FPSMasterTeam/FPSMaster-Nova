package top.fpsmaster.text

import top.fpsmaster.mc

/**
 * Single place the client sends outgoing chat and commands from.
 *
 * The chat signing API changed between generations, so keeping one implementation means a version
 * that needs its own path (1.19.2 signs differently) is fixed once instead of in every caller.
 */
object ChatSender {
    fun send(raw: String) {
        val message = raw.trim()
        if (message.isEmpty()) {
            return
        }

        val connection = mc.connection ?: return
        if (message.startsWith("/")) {
            //? if >=1.20 {
            connection.sendCommand(message.removePrefix("/"))
            //?}
            // 1.19.2 chat/command signing API differs; sending is skipped there rather than faked.
        } else {
            //? if >=1.20 {
            connection.sendChat(message)
            //?}
        }
    }
}
