package top.fpsmaster.command

import top.fpsmaster.command.impl.BindCommand
import top.fpsmaster.command.impl.AuthCommand
import top.fpsmaster.command.impl.ConfigCommand
import top.fpsmaster.command.impl.HelpCommand
import top.fpsmaster.command.impl.SetCommand
import top.fpsmaster.command.impl.ShortcutCommand
import top.fpsmaster.command.impl.TelemetryCommand
import top.fpsmaster.command.impl.ToggleCommand
import top.fpsmaster.logger
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import java.util.Locale

class CommandManager {
    companion object {
        private val registeredCommands = linkedMapOf<String, Command>()
        private val registeredAliases = linkedMapOf<String, Command>()

        @JvmStatic
        fun initialize() {
            register(
                ToggleCommand(),
                SetCommand(),
                HelpCommand(),
                BindCommand(),
                AuthCommand(),
                ShortcutCommand(),
                TelemetryCommand(),
                ConfigCommand()
            )
        }

        fun register(vararg commands: Command) {
            commands.forEach { command ->
                val nameKey = normalize(command.name)
                require(!registeredCommands.containsKey(nameKey)) { "Duplicate command: ${command.name}" }
                registeredCommands[nameKey] = command

                command.aliases.forEach { alias ->
                    val aliasKey = normalize(alias)
                    require(!registeredAliases.containsKey(aliasKey) && !registeredCommands.containsKey(aliasKey)) {
                        "Duplicate command alias: $alias"
                    }
                    registeredAliases[aliasKey] = command
                }
            }
        }

        @JvmStatic
        fun parse(rawInput: String) {
            try {
                val parsed = CommandParser.parse(rawInput)
                val command = findCommand(parsed.commandName) ?: throw UnknownCommandException(parsed.commandName)
                command.execute(CommandContext(rawInput = rawInput, rawArguments = parsed.arguments, command = command, invokedName = parsed.commandName))
            } catch (exception: CommandException) {
                CommandFeedback.error(exception.message ?: "命令执行失败")
            } catch (exception: Exception) {
                logger.error("Unexpected command execution failure", exception)
                CommandFeedback.error("命令执行失败: ${exception.message ?: exception::class.simpleName}")
            }
        }

        @JvmStatic
        fun getPrefix(): String = ClientSettings.commandPrefix.getValue()

        @JvmStatic
        fun hasCommandPrefix(message: String): Boolean {
            val prefix = getPrefix()
            return ClientSettings.clientCommand.getValue() && prefix.isNotEmpty() && message.startsWith(prefix)
        }

        @JvmStatic
        fun isCommandMessage(message: String): Boolean = hasCommandPrefix(message) && message.length > getPrefix().length

        @JvmStatic
        fun stripPrefix(message: String): String = message.removePrefix(getPrefix())

        @JvmStatic
        fun complete(rawInput: String): List<String> {
            val (tokens, endsWithSpace) = CommandParser.splitForCompletion(rawInput)
            if (tokens.isEmpty()) {
                return listCommandNames("")
            }

            if (tokens.size == 1 && !endsWithSpace) {
                return listCommandNames(tokens[0])
            }

            val command = findCommand(tokens[0]) ?: return listCommandNames(tokens[0])
            val arguments = tokens.drop(1).toMutableList()
            if (endsWithSpace) {
                arguments += ""
            }

            val currentArgument = arguments.lastOrNull() ?: ""
            val previousArguments = if (arguments.isEmpty()) emptyList() else arguments.dropLast(1)
            val completionContext = CompletionContext(
                rawInput = rawInput,
                command = command,
                arguments = previousArguments,
                currentArgument = currentArgument,
                argumentIndex = arguments.lastIndex.coerceAtLeast(0)
            )

            return command.complete(completionContext).map { candidate ->
                buildCompletion(command.name, previousArguments, candidate)
            }
        }

        fun commands(): List<Command> = registeredCommands.values.toList()

        fun findCommand(name: String): Command? {
            val key = normalize(name)
            return registeredCommands[key] ?: registeredAliases[key]
        }

        private fun listCommandNames(prefix: String): List<String> {
            val normalizedPrefix = normalize(prefix)
            return registeredCommands.values
                .map { it.name }
                .filter { normalize(it).startsWith(normalizedPrefix) }
                .sorted()
        }

        private fun buildCompletion(commandName: String, arguments: List<String>, candidate: String): String {
            return (listOf(commandName) + arguments + listOf(candidate)
                .filter { it.isNotBlank() }
                .joinToString(" ")).toString()
        }

        private fun normalize(input: String): String = input.lowercase(Locale.ROOT)
    }
}
