package top.fpsmaster.translation

class Language {
    val entries = mutableMapOf<String, String>()

    companion object {
        val translations = mutableMapOf<String, Language>()
        var current: Language? = null

        fun initialize() {
            // TODO: Read local translations
            if (translations.isEmpty()) {
                // TODO: Download zh_CN from fpsmaster.top
            }
            // TODO: Read config, select language
        }

        fun get(key: String): String {
            return current?.entries[key] ?: key
        }
    }
}