package top.fpsmaster.command.impl

import top.fpsmaster.command.Command
import top.fpsmaster.command.CommandContext
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.command.CompletionContext
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.render.ClickGUI
import top.fpsmaster.module.value.Value
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.web.network.packets.PacketRegistryInitializer

class SetCommand : Command(
    name = "set",
    aliases = listOf("s"),
    description = "设置模块选项值",
    usage = "set <module> <option> <value>"
) {
    override fun execute(context: CommandContext) {
        context.requireMinimumArgumentCount(3)

        val moduleName = context.requireArgument(0, "module")
        val optionName = context.requireArgument(1, "option")
        val rawValue = context.arguments.drop(2).joinToString(" ")

        val module = ModuleManager.modules[moduleName.lowercase()]
            ?: throw CommandExecutionException("模块不存在: $moduleName")
        val value = module.values.firstOrNull { it.getIdentity().equals(optionName, ignoreCase = true) }
            ?: throw CommandExecutionException("选项不存在: ${module.identity}.$optionName")

        applyValue(module.identity, value, rawValue)
        ConfigManager.saveDefault()
        PacketRegistryInitializer.broadcastModuleSnapshot()
        context.replySuccess("${module.identity}.${value.getIdentity()} = ${formatValue(value)}")
    }

    override fun complete(context: CompletionContext): List<String> {
        return when (context.argumentIndex) {
            0 -> ModuleManager.modules.keys
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }
                .sorted()

            1 -> {
                val module = ModuleManager.modules[context.arguments.firstOrNull()?.lowercase()]
                module?.values
                    ?.map { it.getIdentity() }
                    ?.filter { it.startsWith(context.currentArgument, ignoreCase = true) }
                    ?.sorted()
                    ?: emptyList()
            }

            2 -> {
                val module = ModuleManager.modules[context.arguments.firstOrNull()?.lowercase()]
                val optionName = context.arguments.getOrNull(1)
                val value = module?.values?.firstOrNull { it.getIdentity().equals(optionName, ignoreCase = true) }
                completeValue(value, context.currentArgument)
            }

            else -> emptyList()
        }
    }

    private fun applyValue(moduleId: String, value: Value<*>, rawValue: String) {
        when (value) {
            is OptionValue -> value.setValue(parseBoolean(rawValue))
            is NumberValue -> value.setValue(rawValue.toDoubleOrNull()
                ?: throw CommandExecutionException("无效数字: $rawValue"))

            is StringValue -> {
                if (moduleId.equals("clickgui", ignoreCase = true) &&
                    value.getIdentity().equals(ClickGUI.commandPrefix.getIdentity(), ignoreCase = true) &&
                    rawValue.isBlank()
                ) {
                    throw CommandExecutionException("命令前缀不能为空")
                }
                value.setValue(rawValue)
            }

            else -> throw CommandExecutionException("不支持的值类型: ${value::class.simpleName}")
        }
    }

    private fun parseBoolean(rawValue: String): Boolean {
        return when (rawValue.lowercase()) {
            "true", "on", "enable", "enabled", "1", "yes" -> true
            "false", "off", "disable", "disabled", "0", "no" -> false
            else -> throw CommandExecutionException("无效布尔值: $rawValue")
        }
    }

    private fun completeValue(value: Value<*>?, prefix: String): List<String> {
        return when (value) {
            is OptionValue -> listOf("true", "false").filter { it.startsWith(prefix, ignoreCase = true) }
            is NumberValue -> listOf(value.getValue().toString())
            is StringValue -> listOf(value.getValue()).filter { it.startsWith(prefix, ignoreCase = true) }
            else -> emptyList()
        }
    }

    private fun formatValue(value: Value<*>): String {
        return when (value) {
            is OptionValue -> value.getValue().toString()
            is NumberValue -> value.getValue().toString()
            is StringValue -> value.getValue()
            else -> "<unknown>"
        }
    }
}
