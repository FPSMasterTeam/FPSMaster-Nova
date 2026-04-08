package top.fpsmaster.command.impl

import top.fpsmaster.command.Command
import top.fpsmaster.command.CommandContext
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.command.CompletionContext
import top.fpsmaster.command.UsageCommandException
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.web.network.packets.PacketRegistryInitializer

class ToggleCommand : Command(
    name = "toggle",
    aliases = listOf("t"),
    description = "切换模块开关状态",
    usage = "toggle <module>"
) {
    override fun execute(context: CommandContext) {
        context.requireArgumentCount(1)
        val moduleName = context.requireArgument(0, "module")
        val module = ModuleManager.modules[moduleName.lowercase()]
            ?: throw CommandExecutionException("模块不存在: $moduleName")

        module.enabled = !module.enabled
        PacketRegistryInitializer.broadcastModuleSnapshot()
        context.replySuccess("${module.identity} 已切换为 ${if (module.enabled) "开启" else "关闭"}")
    }

    override fun complete(context: CompletionContext): List<String> {
        if (context.argumentIndex == 0) {
            return ModuleManager.modules.keys
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }
                .sorted()
        }
        return emptyList()
    }
}
