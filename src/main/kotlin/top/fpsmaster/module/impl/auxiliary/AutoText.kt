package top.fpsmaster.module.impl.auxiliary

import io.github.vlouboos.standaloneevent.api.EventHandler
import org.lwjgl.glfw.GLFW
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.screenCompat
import top.fpsmaster.text.ChatSender

/**
 * Key-to-chat bindings: press the bound key with no screen open and the message is sent.
 *
 * Entries are stored as `keyCode:message` pairs separated by `;`, so they survive in the existing
 * string-valued config without a bespoke serializer. Messages may not contain `;`.
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

        parsed().forEach { entry ->
            if (entry.keyCode == key) {
                ChatSender.send(entry.message)
            }
        }
    }

    data class Entry(val keyCode: Int, val message: String)

    companion object {
        /** Same ceiling as Edge: twenty bindings. */
        const val MAX_ENTRIES = 20

        val entries = StringValue(
            "entries",
            "${GLFW.GLFW_KEY_G}:gg",
            validator = { text -> text.isEmpty() || parse(text).isNotEmpty() }
        )

        @JvmStatic
        fun parsed(): List<Entry> = parse(entries.getValue())

        /** Replace all bindings; duplicate keys keep the first, matching the editor's rule. */
        @JvmStatic
        fun setEntries(list: List<Entry>) {
            entries.setValue(format(list))
        }

        fun format(list: List<Entry>): String = list.asSequence()
            .filter { it.keyCode != GLFW.GLFW_KEY_UNKNOWN && it.message.isNotBlank() && !it.message.contains(';') }
            .distinctBy { it.keyCode }
            .take(MAX_ENTRIES)
            .joinToString(";") { "${it.keyCode}:${it.message}" }

        private fun parse(text: String): List<Entry> = text.split(';')
            .asSequence()
            .mapNotNull { token ->
                val separator = token.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val keyCode = token.substring(0, separator).trim().toIntOrNull() ?: return@mapNotNull null
                val message = token.substring(separator + 1).trim()
                if (keyCode == GLFW.GLFW_KEY_UNKNOWN || message.isEmpty()) null else Entry(keyCode, message)
            }
            .distinctBy { it.keyCode }
            .take(MAX_ENTRIES)
            .toList()
    }
}
