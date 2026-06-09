package top.fpsmaster.command.impl

import top.fpsmaster.command.Command
import top.fpsmaster.command.CommandContext
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.command.CommandKeys
import top.fpsmaster.command.CompletionContext
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.shortcut.ShortcutManager

class ShortcutCommand : Command(
    name = "shortcut",
    aliases = listOf("sc"),
    description = "管理快捷动作",
    usage = "shortcut <list/set/remove/clear> [args]"
) {
    override fun execute(context: CommandContext) {
        context.requireMinimumArgumentCount(1)
        val action = context.requireArgument(0, "action").lowercase()

        when (action) {
            "list" -> {
                context.requireArgumentCount(1)
                val shortcuts = ShortcutManager.list()
                context.replyInfo(
                    if (shortcuts.isEmpty()) {
                        "没有快捷动作"
                    } else {
                        shortcuts.joinToString(", ") { "${it.name}:${CommandKeys.format(it.key)}" }
                    }
                )
            }

            "set" -> {
                context.requireMinimumArgumentCount(4)
                val name = context.requireArgument(1, "name")
                val rawKey = context.requireArgument(2, "key")
                val key = CommandKeys.parse(rawKey)
                    ?: throw CommandExecutionException("未知按键: $rawKey")
                val shortcutAction = parseAction(context.arguments.drop(3))

                ShortcutManager.set(name, key, listOf(shortcutAction))
                ConfigManager.saveActive()
                context.replySuccess("快捷动作已设置: $name -> ${CommandKeys.format(key)}")
            }

            "remove" -> {
                context.requireArgumentCount(2)
                val name = context.requireArgument(1, "name")
                if (!ShortcutManager.remove(name)) {
                    throw CommandExecutionException("快捷动作不存在: $name")
                }
                ConfigManager.saveActive()
                context.replySuccess("快捷动作已删除: $name")
            }

            "clear" -> {
                context.requireArgumentCount(1)
                ShortcutManager.clear()
                ConfigManager.saveActive()
                context.replySuccess("快捷动作已清空")
            }

            else -> throw CommandExecutionException("不支持的快捷动作操作: $action")
        }
    }

    override fun complete(context: CompletionContext): List<String> {
        return when (context.argumentIndex) {
            0 -> listOf("list", "set", "remove", "clear")
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }

            1 -> {
                val action = context.arguments.firstOrNull()?.lowercase()
                if (action == "remove") {
                    ShortcutManager.list()
                        .map { it.name }
                        .filter { it.startsWith(context.currentArgument, ignoreCase = true) }
                        .sorted()
                } else {
                    emptyList()
                }
            }

            2 -> {
                val action = context.arguments.firstOrNull()?.lowercase()
                if (action == "set") {
                    CommandKeys.complete(context.currentArgument)
                } else {
                    emptyList()
                }
            }

            3 -> {
                val action = context.arguments.firstOrNull()?.lowercase()
                if (action == "set") {
                    listOf("message", "quit")
                        .filter { it.startsWith(context.currentArgument, ignoreCase = true) }
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }
    }

    private fun parseAction(arguments: List<String>): ShortcutManager.Action {
        val type = arguments.firstOrNull()?.lowercase()
            ?: throw CommandExecutionException("缺少快捷动作类型")
        return when (type) {
            "message", "send_message", "send-message" -> {
                val message = arguments.drop(1).joinToString(" ").trim()
                if (message.isEmpty()) {
                    throw CommandExecutionException("消息内容不能为空")
                }
                ShortcutManager.Action(ShortcutManager.ActionType.SEND_MESSAGE, message)
            }

            "quit", "quit_network", "quit-network" -> {
                if (arguments.size != 1) {
                    throw CommandExecutionException("退出服务器动作不需要额外参数")
                }
                ShortcutManager.Action(ShortcutManager.ActionType.QUIT_NETWORK)
            }

            else -> throw CommandExecutionException("不支持的快捷动作类型: $type")
        }
    }
}
