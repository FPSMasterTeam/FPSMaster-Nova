package top.fpsmaster.translation

import top.fpsmaster.module.impl.auxiliary.ClientSettings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class Language {
    val entries = mutableMapOf<String, String>()

    companion object {
        private val languageCodes = arrayOf("en_us", "zh_cn")
        val translations = mutableMapOf<String, Language>()
        var current: Language? = null

        fun initialize() {
            if (translations.isEmpty()) {
                languageCodes.forEach { code ->
                    translations[code] = readLocal(code)
                }
            }
            current = translations[languageCode()]
        }

        fun get(key: String): String {
            val selected = translations[languageCode()]
            if (selected != null && current !== selected) {
                current = selected
            }
            return current?.entries[key] ?: key
        }

        private fun languageCode(): String {
            val index = ClientSettings.language.getValue().toInt().coerceIn(languageCodes.indices)
            return languageCodes[index]
        }

        private fun readLocal(code: String): Language {
            val language = Language()
            val stream = Language::class.java.classLoader
                .getResourceAsStream("assets/fpsmaster/lang/$code.lang")
                ?: return language

            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        return@forEach
                    }

                    val separator = trimmed.indexOf('=')
                    if (separator <= 0) {
                        return@forEach
                    }

                    language.entries[trimmed.substring(0, separator)] = trimmed.substring(separator + 1)
                }
            }
            return language
        }
    }
}
