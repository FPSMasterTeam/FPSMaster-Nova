package top.fpsmaster.shortcut

import io.github.vlouboos.standaloneevent.api.EventHandler
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.mc

object ShortcutManager {
    private val shortcuts = mutableListOf<Shortcut>()

    fun initialize() {
        StandaloneEventAPI.getApi().register(this)
    }

    fun set(name: String, key: Int, actions: List<Action>) {
        remove(name)
        shortcuts += Shortcut(name, key, actions.toMutableList())
    }

    fun remove(name: String): Boolean {
        return shortcuts.removeIf { it.name.equals(name, ignoreCase = true) }
    }

    fun clear() {
        shortcuts.clear()
    }

    fun list(): List<Shortcut> {
        return shortcuts.map { it.copy(actions = it.actions.toMutableList()) }
    }

    fun replaceAll(newShortcuts: List<Shortcut>) {
        shortcuts.clear()
        shortcuts += newShortcuts.map { it.copy(actions = it.actions.toMutableList()) }
    }

    fun snapshot(): List<Shortcut> {
        return list()
    }

    @JvmStatic
    @Suppress("unused")
    @EventHandler
    fun onKey(event: KeyEvent) {
        shortcuts
            .filter { it.key == event.key.value }
            .forEach { shortcut -> shortcut.actions.forEach(Action::run) }
    }

    data class Shortcut(
        val name: String = "",
        val key: Int = 0,
        val actions: MutableList<Action> = mutableListOf()
    )

    data class Action(
        val type: ActionType = ActionType.SEND_MESSAGE,
        val context: String = ""
    ) {
        fun run() {
            when (type) {
                ActionType.SEND_MESSAGE -> sendMessage(context)
                ActionType.QUIT_NETWORK -> quitNetwork()
            }
        }
    }

    enum class ActionType {
        SEND_MESSAGE,
        QUIT_NETWORK
    }

    private fun sendMessage(message: String) {
        val connection = mc.connection ?: return
        if (message.startsWith("/")) {
            connection.sendCommand(message.removePrefix("/"))
        } else {
            connection.sendChat(message)
        }
    }

    private fun quitNetwork() {
        //? if >=1.21.5 {
        mc.disconnect(JoinMultiplayerScreen(TitleScreen()), false)
        //?} else {
        /*mc.clearLevel(JoinMultiplayerScreen(TitleScreen()))*/
        //?}
    }
}
