package top.fpsmaster.command.impl

import top.fpsmaster.command.Command
import top.fpsmaster.command.CommandContext
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.command.CompletionContext
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.telemetry.TelemetryReporter

class TelemetryCommand : Command(
    name = "telemetry",
    aliases = listOf("presence"),
    description = "管理匿名数据上报",
    usage = "telemetry <status/on/off>"
) {
    override fun execute(context: CommandContext) {
        context.requireArgumentCount(1)
        when (val action = context.requireArgument(0, "action").lowercase()) {
            "status" -> {
                val state = if (TelemetryReporter.anonymousDataEnabled) "已开启" else "已关闭"
                context.replyInfo("匿名数据上报: $state，实例 ID: ${TelemetryReporter.telemetryInstanceId}")
            }

            "on", "enable", "enabled" -> {
                TelemetryReporter.setEnabled(true)
                ConfigManager.saveActive()
                context.replySuccess("匿名数据上报已开启")
            }

            "off", "disable", "disabled" -> {
                TelemetryReporter.setEnabled(false)
                ConfigManager.saveActive()
                context.replySuccess("匿名数据上报已关闭")
            }

            else -> throw CommandExecutionException("不支持的上报操作: $action")
        }
    }

    override fun complete(context: CompletionContext): List<String> {
        return if (context.argumentIndex == 0) {
            listOf("status", "on", "off")
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }
        } else {
            emptyList()
        }
    }
}
