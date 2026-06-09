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
    usage = "config <create/load/save/delete/rename/import/export/alloff/list/current> [args]"
) {
    override fun execute(context: CommandContext) {
        context.requireMinimumArgumentCount(1)
        val action = context.requireArgument(0, "action").lowercase()

        when (action) {
            "create" -> {
                context.requireArgumentCount(2)
                val name = context.requireArgument(1, "name")
                ConfigManager.create(name)
                context.replySuccess("配置已创建: $name")
            }

            "load" -> {
                context.requireArgumentCount(2)
                val name = context.requireArgument(1, "name")
                ConfigManager.loadProfile(name)
                context.replySuccess("配置已加载: $name")
            }

            "save" -> {
                if (context.arguments.size == 1) {
                    ConfigManager.saveActive()
                    context.replySuccess("配置已保存: ${ConfigManager.activeName()}")
                } else {
                    context.requireArgumentCount(2)
                    val name = context.requireArgument(1, "name")
                    ConfigManager.saveAs(name)
                    context.replySuccess("配置已保存并切换: $name")
                }
            }

            "delete" -> {
                context.requireArgumentCount(2)
                val name = context.requireArgument(1, "name")
                ConfigManager.delete(name)
                context.replySuccess("配置已删除: $name")
            }

            "rename" -> {
                context.requireArgumentCount(3)
                val oldName = context.requireArgument(1, "oldName")
                val newName = context.requireArgument(2, "newName")
                val renamed = ConfigManager.rename(oldName, newName)
                context.replySuccess("配置已重命名: $oldName -> $renamed")
            }

            "import" -> {
                context.requireArgumentCount(2)
                val path = context.requireArgument(1, "path")
                val name = ConfigManager.importProfile(path)
                context.replySuccess("配置已导入: $name")
            }

            "export" -> {
                context.requireArgumentCount(2)
                val path = context.requireArgument(1, "path")
                ConfigManager.exportActive(path)
                context.replySuccess("当前配置已导出: $path")
            }

            "alloff" -> {
                context.requireArgumentCount(1)
                ConfigManager.resetActiveToAllOff()
                context.replySuccess("当前配置已全部关闭: ${ConfigManager.activeName()}")
            }

            "list" -> {
                context.requireArgumentCount(1)
                val names = ConfigManager.listNames()
                context.replyInfo(if (names.isEmpty()) "没有配置" else names.joinToString(", "))
            }

            "current" -> {
                context.requireArgumentCount(1)
                context.replyInfo("当前配置: ${ConfigManager.activeName()}")
            }

            else -> throw CommandExecutionException("不支持的配置操作: $action")
        }
    }

    override fun complete(context: CompletionContext): List<String> {
        return when (context.argumentIndex) {
            0 -> listOf("create", "load", "save", "delete", "rename", "import", "export", "alloff", "list", "current")
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }

            1 -> {
                val action = context.arguments.firstOrNull()?.lowercase()
                if (action == "load" || action == "delete" || action == "rename") {
                    ConfigManager.listNames(context.currentArgument)
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }
    }
}
