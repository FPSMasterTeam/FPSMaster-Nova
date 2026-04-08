package top.fpsmaster.command

open class CommandException(message: String) : RuntimeException(message)

class UnknownCommandException(name: String) : CommandException("未知命令: $name")

class UsageCommandException(command: Command, detail: String? = null) : CommandException(
    buildString {
        append("用法: ")
        append(command.usage)
        if (!detail.isNullOrBlank()) {
            append(" (")
            append(detail)
            append(')')
        }
    }
)

class CommandExecutionException(message: String) : CommandException(message)
