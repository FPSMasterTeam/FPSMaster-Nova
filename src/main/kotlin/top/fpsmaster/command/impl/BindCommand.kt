package top.fpsmaster.command.impl

import top.fpsmaster.command.Command
import top.fpsmaster.command.CommandContext
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.command.CommandKeys
import top.fpsmaster.command.CompletionContext
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.module.ModuleManager

class BindCommand : Command(
    name = "bind",
    aliases = listOf("b"),
    description = "为模块绑定按键",
    usage = "bind <module> <key>"
) {
    override fun execute(context: CommandContext) {
        context.requireArgumentCount(2)
        val moduleName = context.requireArgument(0, "module")
        val rawKey = context.requireArgument(1, "key")

        val module = ModuleManager.modules[moduleName.lowercase()]
            ?: throw CommandExecutionException("模块不存在: $moduleName")
        val key = CommandKeys.parse(rawKey)
            ?: throw CommandExecutionException("未知按键: $rawKey")

        module.key = key
        ConfigManager.saveDefault()
        context.replySuccess("${module.identity} 已绑定到 ${CommandKeys.format(key)}")
    }

    override fun complete(context: CompletionContext): List<String> {
        return when (context.argumentIndex) {
            0 -> ModuleManager.modules.keys
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }
                .sorted()

            1 -> CommandKeys.complete(context.currentArgument)

            else -> emptyList()
        }
    }
}
