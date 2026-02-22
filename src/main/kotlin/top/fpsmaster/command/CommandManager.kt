package top.fpsmaster.command

import org.jetbrains.annotations.NotNull
import top.fpsmaster.command.impl.Toggle

class CommandManager {
    companion object {
        val commands = hashMapOf<String, Command>()

        fun addCommand(vararg commands: Command) {
            commands.forEach {
                Companion.commands[it.identity] = it
            }
        }

        @JvmStatic
        fun initialize() {
            addCommand(
                Toggle()
            )
        }

        @JvmStatic
        fun parse(@NotNull command: String) {
            val arr = command.split(" ")
            val cmd = commands[arr[0].lowercase()]
            if (cmd != null) {
                cmd.execute(arr.toTypedArray().copyOfRange(1, arr.size))
            } else {
                // TODO: Push a notification to tell them about inexistence
            }
        }
    }
}