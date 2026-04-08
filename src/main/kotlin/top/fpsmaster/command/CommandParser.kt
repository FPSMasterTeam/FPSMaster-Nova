package top.fpsmaster.command

data class ParsedCommandInput(
    val commandName: String,
    val arguments: List<String>
)

object CommandParser {
    fun parse(input: String): ParsedCommandInput {
        val tokens = tokenize(input)
        if (tokens.isEmpty()) {
            throw CommandException("请输入命令")
        }

        return ParsedCommandInput(
            commandName = tokens.first(),
            arguments = tokens.drop(1)
        )
    }

    fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false

        input.forEach { char ->
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }

                char == '\\' -> escaping = true

                quote != null -> {
                    if (char == quote) {
                        quote = null
                    } else {
                        current.append(char)
                    }
                }

                char == '"' || char == '\'' -> quote = char

                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.setLength(0)
                    }
                }

                else -> current.append(char)
            }
        }

        if (escaping) {
            current.append('\\')
        }

        if (quote != null) {
            throw CommandException("存在未闭合的引号")
        }

        if (current.isNotEmpty()) {
            tokens += current.toString()
        }

        return tokens
    }

    fun splitForCompletion(input: String): Pair<List<String>, Boolean> {
        if (input.isBlank()) {
            return emptyList<String>() to input.endsWith(' ')
        }

        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return Pair(tokens, input.lastOrNull()?.isWhitespace() == true)
    }
}
