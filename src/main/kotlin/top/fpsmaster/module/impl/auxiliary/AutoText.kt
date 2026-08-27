package top.fpsmaster.module.impl.auxiliary

import io.github.vlouboos.standaloneevent.api.EventHandler
import org.lwjgl.glfw.GLFW
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ListValue
import top.fpsmaster.screenCompat
import top.fpsmaster.text.ChatSender

/**
 * Key-to-chat bindings: press the bound key with no screen open and the message is sent.
 */
class AutoText : Module("auto-text", Category.AUXILIARY) {
    init {
        values.add(entries)
    }

    @EventHandler
    fun onKey(event: KeyEvent) {
        val key = event.key.value
        if (key == GLFW.GLFW_KEY_UNKNOWN || mc.screenCompat != null || mc.player == null) {
            return
        }
        entries.forKey(key)?.let { ChatSender.send(it.text) }
    }

    companion object {
        val entries = ListValue.autoText(
            "entries",
            listOf(ListValue.Entry("gg", GLFW.GLFW_KEY_G))
        )
    }
}
