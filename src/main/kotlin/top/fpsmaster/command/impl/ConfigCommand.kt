package top.fpsmaster.command.impl

import top.fpsmaster.command.Command
import top.fpsmaster.command.CommandContext
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.command.CompletionContext
import top.fpsmaster.config.ConfigManager

class ConfigCommand : Command(
    name = "config",
    aliases = listOf("cfg"),
    description = "创建或加载配置",
    usage = "config <create/load> <name>"
) {
    override fun execute(context: CommandContext) {
        context.requireArgumentCount(2)
        val action = context.requireArgument(0, "action").lowercase()
        val name = context.requireArgument(1, "name")

        when (action) {
            "create" -> {
                ConfigManager.create(name)
                context.replySuccess("配置已创建: $name")
            }

            "load" -> {
                ConfigManager.load(name)
                context.replySuccess("配置已加载: $name")
            }

            else -> throw CommandExecutionException("不支持的配置操作: $action")
        }
    }

    override fun complete(context: CompletionContext): List<String> {
        return when (context.argumentIndex) {
            0 -> listOf("create", "load")
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }

            1 -> {
                val action = context.arguments.firstOrNull()?.lowercase()
                if (action == "load") {
                    ConfigManager.listNames(context.currentArgument)
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }
    }
}
