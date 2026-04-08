package top.fpsmaster.command

import org.lwjgl.glfw.GLFW
import java.lang.reflect.Modifier
import java.util.Locale

object CommandKeys {
    private val keyByName = linkedMapOf<String, Int>()
    private val displayByKey = linkedMapOf<Int, String>()

    init {
        GLFW::class.java.fields
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    field.type == Int::class.javaPrimitiveType &&
                    field.name.startsWith("GLFW_KEY_")
            }
            .forEach { field ->
                val value = field.getInt(null)
                val displayName = field.name.removePrefix("GLFW_KEY_")
                val normalized = normalize(displayName)
                keyByName[normalized] = value
                displayByKey[value] = displayName
            }

        keyByName["none"] = 0
        keyByName["unbind"] = 0
        keyByName["unset"] = 0
        displayByKey[0] = "NONE"
    }

    fun parse(raw: String): Int? {
        val normalized = normalize(raw)
        return keyByName[normalized]
    }

    fun format(key: Int): String {
        return displayByKey[key] ?: "KEY_$key"
    }

    fun complete(prefix: String): List<String> {
        val normalizedPrefix = normalize(prefix)
        return keyByName.keys
            .filter { it.startsWith(normalizedPrefix) }
            .mapNotNull { keyByName[it]?.let(::format) }
            .distinct()
            .sorted()
            .take(20)
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
    }
}
