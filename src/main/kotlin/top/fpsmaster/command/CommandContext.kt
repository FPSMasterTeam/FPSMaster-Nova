package top.fpsmaster.command

data class CommandContext(
    val rawInput: String,
    val rawArguments: List<String>,
    val command: Command
) {
    val arguments: List<String> = rawArguments

    fun requireArgument(index: Int, name: String): String {
        return arguments.getOrNull(index) ?: throw UsageCommandException(command, "缺少参数: $name")
    }

    fun requireArgumentCount(count: Int) {
        if (arguments.size != count) {
            throw UsageCommandException(command)
        }
    }

    fun requireMinimumArgumentCount(count: Int) {
        if (arguments.size < count) {
            throw UsageCommandException(command)
        }
    }

    fun replyInfo(message: String) = CommandFeedback.info(message)

    fun replySuccess(message: String) = CommandFeedback.success(message)

    fun replyError(message: String) = CommandFeedback.error(message)
}

data class CompletionContext(
    val rawInput: String,
    val command: Command,
    val arguments: List<String>,
    val currentArgument: String,
    val argumentIndex: Int
)
