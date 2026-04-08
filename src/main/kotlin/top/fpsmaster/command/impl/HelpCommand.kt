package top.fpsmaster.command.impl

import top.fpsmaster.command.Command
import top.fpsmaster.command.CommandContext
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.command.CommandManager
import top.fpsmaster.command.CompletionContext

class HelpCommand : Command(
    name = "help",
    aliases = listOf("h"),
    description = "查看命令帮助",
    usage = "help [command]"
) {
    override fun execute(context: CommandContext) {
        if (context.arguments.isEmpty()) {
            context.replyInfo("可用命令:")
            CommandManager.commands().forEach { command ->
                context.replyInfo("${command.name} - ${command.description}")
            }
            return
        }

        val commandName = context.requireArgument(0, "command")
        val command = CommandManager.findCommand(commandName)
            ?: throw CommandExecutionException("未知命令: $commandName")

        context.replyInfo("${command.name} - ${command.description}")
        context.replyInfo("用法: ${command.usage}")
        if (command.aliases.isNotEmpty()) {
            context.replyInfo("别名: ${command.aliases.joinToString(", ")}")
        }
    }

    override fun complete(context: CompletionContext): List<String> {
        if (context.argumentIndex == 0) {
            return CommandManager.commands()
                .map { it.name }
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }
                .sorted()
        }
        return emptyList()
    }
}
