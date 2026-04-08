package top.fpsmaster.command

abstract class Command(
    val name: String,
    val aliases: List<String>,
    val description: String,
    val usage: String
) {
    fun names(): List<String> = listOf(name) + aliases

    abstract fun execute(context: CommandContext)

    open fun complete(context: CompletionContext): List<String> = emptyList()
}
