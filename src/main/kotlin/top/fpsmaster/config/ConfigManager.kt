package top.fpsmaster.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import top.fpsmaster.logger
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.cosmetic.CosmeticManager
import top.fpsmaster.hud.HudManager
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.value.Value
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.ColorValue
import top.fpsmaster.module.value.impl.KeyValue
import top.fpsmaster.module.value.impl.ListValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.shortcut.ShortcutManager
import top.fpsmaster.mc
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.Locale
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.reader
import kotlin.io.path.readText

object ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val saveLock = Any()

    /**
     * Profile writes (fsync included) run here, not on the render thread: a module hotkey or a
     * slider drag in the ClickGUI saves, and on a slow disk or under an antivirus scan that was a
     * visible hitch. Latest-wins per path: a burst of saves writes the final state once. Anything
     * that reads or replaces a profile file first calls [awaitWrites].
     */
    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "fpsmaster-config-io").apply { isDaemon = true }
    }
    private val pendingWrites = ConcurrentHashMap<Path, String>()

    private val backups by lazy { ConfigBackups(ensureConfigDirectory().resolve("backups")) }

    /** Something the player should hear about from startup (restore from backup, reset). */
    @Volatile
    private var startupNotice: Pair<String, String>? = null

    private val validNameRegex = Regex("^[a-zA-Z0-9._-]+$")
    private const val DEFAULT_CONFIG_NAME = "default"
    private const val ACTIVE_PROFILE_STATE = "active_profile.txt"
    private var activeProfileName = DEFAULT_CONFIG_NAME
    private var convertedEdgeProfile = false
    private var profileCarriesHudPlacement = false
    var musicVolume: Double = 75.0
        private set
    var musicPlaybackMode: String = "SEQUENTIAL"
        private set
    var background: String = "panorama_1"
        private set
    var oobeCompleted: Boolean = false
        private set

    fun completeOobe() {
        oobeCompleted = true
        saveDefault()
    }
    var antiCheatEnabled: Boolean = true
        private set
    var classicBackgroundColor: Int = 0xFF000000.toInt()
        private set
    var classicBackgroundHue: Float = 0f
        private set
    var classicBackgroundSaturation: Float = 0f
        private set
    var classicBackgroundBrightness: Float = 0f
        private set
    var classicBackgroundAlpha: Float = 1f
        private set
    var classicBackgroundMode: String = "STATIC"
        private set

    fun loadDefault() {
        migrateLegacyDefaultIfNeeded()
        migrateEdgeProfilesIfNeeded()
        val profileName = loadActiveProfileName()
        val path = configPath(profileName)
        if (path.exists() && path.isRegularFile()) {
            try {
                load(profileName)
                // The file just loaded is known to be good: keep a copy to fall back on.
                runCatching { backups.snapshot(profileName, path.readText(), force = true) }
                    .onFailure { logger.warn("Could not back up config '{}'", profileName, it) }
            } catch (exception: Exception) {
                // Any failure, not only the ones load() anticipates: a crash here would repeat on
                // every launch until the player found and deleted the file by hand.
                val invalidPath = backupInvalidConfig(path)
                logger.warn("Failed to load config '{}'; moved it to {}", profileName, invalidPath.fileName, exception)
                val restored = restoreFromBackups(profileName, path)
                startupNotice = if (restored != null) {
                    "configbackup.restored" to ConfigBackups.label(restored)
                } else {
                    saveDefault()
                    "configbackup.reset" to invalidPath.fileName.toString()
                }
            }
        } else {
            saveDefault()
        }
        migrateLegacyHudFileIfNeeded()
        rewriteConvertedEdgeProfile()
    }

    /**
     * Newest to oldest, the first backup that loads cleanly becomes the profile again. Null when
     * none does; the profile keeps whatever the failed attempts applied and gets rewritten.
     */
    private fun restoreFromBackups(profileName: String, path: Path): Path? {
        for (backup in backups.list(profileName)) {
            try {
                loadFrom(backup, profileName)
                AtomicFiles.write(path, backup.readText())
                logger.info("Restored config '{}' from backup {}", profileName, backup.fileName)
                return backup
            } catch (exception: Exception) {
                logger.warn("Backup {} of '{}' does not load either", backup.fileName, profileName, exception)
            }
        }
        return null
    }

    /** Title and description keys plus argument for the one-time startup notice, if any. */
    fun takeStartupNotice(): Pair<String, String>? = startupNotice.also { startupNotice = null }

    /**
     * Puts the active profile back to its newest backup that differs from what is on disk now. The
     * current state is backed up first, so a second rollback undoes the first. Returns the label of
     * the restored backup.
     */
    fun rollbackActive(): String {
        awaitWrites()
        val path = configPath(activeProfileName)
        val current = runCatching { path.readText() }.getOrNull()
        val target = backups.latestDifferentFrom(activeProfileName, current)
            ?: throw CommandExecutionException(top.fpsmaster.translation.Language.get("configbackup.none"))
        val restored = target.readText()
        current?.let { backups.snapshot(activeProfileName, it, force = true) }
        loadFrom(target, activeProfileName)
        AtomicFiles.write(path, restored)
        return ConfigBackups.label(target)
    }

    /** Waits for queued profile writes, so files are current before they are read or replaced. */
    fun awaitWrites() {
        try {
            io.submit {}.get(10, TimeUnit.SECONDS)
        } catch (exception: Exception) {
            logger.warn("Timed out waiting for config writes", exception)
        }
    }

    fun saveDefault() {
        saveActive()
    }

    fun saveActive() {
        saveSnapshot(configPath(activeProfileName))
    }

    fun activeName(): String {
        return activeProfileName
    }

    fun create(name: String) {
        val path = configPath(name)
        if (path.exists()) {
            throw CommandExecutionException("配置已存在: $name")
        }

        saveSnapshot(path)
    }

    fun saveAs(name: String) {
        val profileName = sanitizeName(name)
        saveSnapshot(configPath(profileName))
        activeProfileName = profileName
        saveActiveProfileName()
    }

    fun loadProfile(name: String) {
        val profileName = sanitizeName(name)
        if (profileName != activeProfileName && configPath(activeProfileName).exists()) {
            saveActive()
        }
        load(profileName)
        activeProfileName = profileName
        saveActiveProfileName()
        rewriteConvertedEdgeProfile()
    }

    fun load(name: String) {
        awaitWrites()
        val path = configPath(name)
        if (!path.exists() || !path.isRegularFile()) {
            throw CommandExecutionException("配置不存在: $name")
        }
        loadFrom(path, name)
    }

    private fun loadFrom(path: Path, name: String) {
        val root = try {
            val json = path.reader().use { JsonParser.parseReader(it) }
            json?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (exception: IllegalStateException) {
            throw CommandExecutionException("配置文件无效: $name")
        } catch (exception: JsonParseException) {
            throw CommandExecutionException("配置文件无效: $name")
        } ?: throw CommandExecutionException("配置文件无效: $name")

        if (isEdgeConfig(root)) {
            convertedEdgeProfile = true
            loadEdgeConfig(root)
            return
        }

        // Valid JSON with a field of the wrong type (hand edits, a value type that changed between
        // versions) throws here rather than in the parser.
        val config = try {
            gson.fromJson(root, ConfigFile::class.java)
        } catch (exception: JsonParseException) {
            throw CommandExecutionException("配置文件无效: $name")
        } ?: throw CommandExecutionException("配置文件无效: $name")

        config.modules.forEach { moduleEntry ->
            val module = ModuleManager.modules[moduleEntry.id.lowercase()] ?: return@forEach
            module.key = moduleEntry.key
            val legacyChannels = mutableMapOf<String, MutableMap<String, Double>>()
            moduleEntry.values.entrySet().forEach { valueEntry ->
                val value = module.values.firstOrNull { it.getIdentity().equals(valueEntry.key, ignoreCase = true) }
                if (value == null) {
                    collectLegacyColorChannel(legacyChannels, valueEntry.key, valueEntry.value)
                } else {
                    // One value of the wrong shape (a string where a number was, an object where a
                    // boolean was) costs that value, not the whole profile.
                    try {
                        applyValue(value, valueEntry.value)
                    } catch (exception: RuntimeException) {
                        logger.warn(
                            "Ignoring config value {}.{}: {}",
                            module.identity, valueEntry.key, exception.toString()
                        )
                    }
                }
            }
            applyLegacyColorChannels(module, legacyChannels)
            if (module.persistEnabled) {
                module.enabled = moduleEntry.enabled
            }
        }

        ShortcutManager.replaceAll(
            config.shortcuts.map { shortcut ->
                ShortcutManager.Shortcut(
                    name = shortcut.name,
                    key = shortcut.key,
                    actions = shortcut.actions.map { action ->
                        ShortcutManager.Action(
                            type = action.type,
                            context = action.context
                        )
                    }.toMutableList()
                )
            }
        )
        config.client?.let(::applyClientPreferences)
        setMusicVolume(
            config.client?.musicVolume
                ?: config.client?.volume?.let { it * 100.0 }
                ?: config.musicVolume
                ?: config.volume?.let { it * 100.0 }
                ?: musicVolume
        )
        musicPlaybackMode = config.client?.musicPlaybackMode ?: "SEQUENTIAL"
        profileCarriesHudPlacement = config.hud != null
        config.hud?.let(::applyHudComponents)
    }

    fun delete(name: String) {
        awaitWrites()
        val profileName = sanitizeName(name)
        if (profileName == DEFAULT_CONFIG_NAME) {
            throw CommandExecutionException("不能删除默认配置")
        }
        if (profileName == activeProfileName) {
            throw CommandExecutionException("不能删除当前正在使用的配置")
        }

        val path = configPath(profileName)
        if (!path.exists()) {
            throw CommandExecutionException("配置不存在: $name")
        }
        path.deleteIfExists()
    }

    fun rename(oldName: String, newName: String): String {
        awaitWrites()
        val sourceName = sanitizeName(oldName)
        val targetName = sanitizeName(newName)
        if (sourceName == DEFAULT_CONFIG_NAME) {
            throw CommandExecutionException("不能重命名默认配置")
        }

        val sourcePath = configPath(sourceName)
        val targetPath = configPath(targetName)
        if (!sourcePath.exists()) {
            throw CommandExecutionException("配置不存在: $oldName")
        }
        if (sourceName != targetName && targetPath.exists()) {
            throw CommandExecutionException("配置已存在: $newName")
        }

        if (sourceName != targetName) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
        if (activeProfileName == sourceName) {
            activeProfileName = targetName
            saveActiveProfileName()
        }
        return targetName
    }

    fun importProfile(source: String): String {
        val sourcePath = Paths.get(source)
        if (!sourcePath.exists() || !sourcePath.isRegularFile()) {
            throw CommandExecutionException("导入文件不存在: $source")
        }

        val fileName = sourcePath.fileName.toString().removeSuffix(".json")
        val profileName = sanitizeName(fileName)
        awaitWrites()
        Files.copy(sourcePath, configPath(profileName), StandardCopyOption.REPLACE_EXISTING)
        return profileName
    }

    fun exportActive(target: String) {
        saveActive()
        awaitWrites()
        val targetPath = Paths.get(target)
        targetPath.parent?.let { Files.createDirectories(it) }
        Files.copy(configPath(activeProfileName), targetPath, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Export a profile by name into the `fpsmaster/exports` folder; returns the written path. */
    fun exportProfile(name: String): String {
        val profile = sanitizeName(name)
        if (profile == activeProfileName) {
            saveActive()
        }
        awaitWrites()
        val source = configPath(profile)
        if (!source.exists() || !source.isRegularFile()) {
            throw CommandExecutionException("档案不存在: $name")
        }
        val exportsDir = ensureConfigDirectory().parent.resolve("exports")
        Files.createDirectories(exportsDir)
        val target = exportsDir.resolve("$profile.json")
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        return target.toString()
    }

    fun resetActiveToAllOff() {
        ModuleManager.modules.values.forEach { module ->
            module.enabled = false
        }
        saveActive()
    }

    fun listNames(prefix: String = ""): List<String> {
        val directory = ensureConfigDirectory()
        return directory.listDirectoryEntries("*.json")
            .map { it.fileName.toString().removeSuffix(".json") }
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .sorted()
    }

    data class ProfileInfo(val name: String, val lastModified: Long, val bytes: Long)

    fun listProfiles(): List<ProfileInfo> {
        val directory = ensureConfigDirectory()
        return directory.listDirectoryEntries("*.json")
            .map { path ->
                ProfileInfo(
                    name = path.fileName.toString().removeSuffix(".json"),
                    lastModified = path.toFile().lastModified(),
                    bytes = path.toFile().length()
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun isDefaultProfile(name: String): Boolean = sanitizeName(name) == DEFAULT_CONFIG_NAME

    private fun saveSnapshot(path: Path) {
        ensureConfigDirectory()
        val profileName = path.fileName.toString().removeSuffix(".json")
        val config = ConfigFile(
            profile = ConfigProfile(name = profileName),
            modules = ModuleManager.modules.values.map { module ->
                ConfigModule(
                    id = module.identity,
                    enabled = module.persistEnabled && module.enabled,
                    key = module.key,
                    values = buildJsonObject {
                        module.values.forEach { value ->
                            add(value.getIdentity(), toJson(value))
                        }
                    }
                )
            },
            shortcuts = ShortcutManager.snapshot().map { shortcut ->
                ConfigShortcut(
                    name = shortcut.name,
                    key = shortcut.key,
                    actions = shortcut.actions.map { action ->
                        ConfigShortcutAction(
                            type = action.type,
                            context = action.context
                        )
                    }
                )
            },
            client = ConfigClient(
                musicVolume = musicVolume,
                volume = musicVolume / 100.0,
                musicPlaybackMode = musicPlaybackMode,
                background = background,
                oobeCompleted = oobeCompleted,
                antiCheatEnabled = antiCheatEnabled,
                classicBackgroundColor = classicBackgroundColor,
                classicBackgroundHue = classicBackgroundHue,
                classicBackgroundSaturation = classicBackgroundSaturation,
                classicBackgroundBrightness = classicBackgroundBrightness,
                classicBackgroundAlpha = classicBackgroundAlpha,
                classicBackgroundMode = classicBackgroundMode,
                cosmeticCapeId = CosmeticManager.selectedCapeId,
                cosmeticWingsId = CosmeticManager.selectedWingsId,
                cosmeticWingsEnabled = CosmeticManager.wingsEnabled,
                capeAnimationEnabled = CosmeticManager.capeAnimationEnabled,
                cosmeticWingScale = CosmeticManager.savedWingScale
            ),
            hud = HudManager.components.values.map { component ->
                ConfigHudComponent(
                    id = component.id,
                    x = component.x,
                    y = component.y,
                    scale = component.scale,
                    visible = component.visible,
                    relativeX = component.relativeX.takeUnless { it.isNaN() },
                    relativeY = component.relativeY.takeUnless { it.isNaN() }
                )
            }
        )

        enqueueWrite(path, gson.toJson(config))
    }

    private fun enqueueWrite(path: Path, content: String) {
        if (pendingWrites.put(path, content) != null) {
            return // the queued task has not run yet and will pick this newer content up
        }
        io.execute {
            val latest = pendingWrites.remove(path) ?: return@execute
            try {
                synchronized(saveLock) {
                    AtomicFiles.write(path, latest)
                }
                val profile = path.fileName.toString().removeSuffix(".json")
                backups.snapshot(profile, latest)
            } catch (exception: Exception) {
                logger.error("Failed to save config {}", path.fileName, exception)
                mc.execute {
                    top.fpsmaster.notification.NotificationManager.add(
                        top.fpsmaster.translation.Language.get("configbackup.title"),
                        top.fpsmaster.translation.Language.get("configbackup.savefailed"),
                        4f,
                        top.fpsmaster.prism.overlay.NotificationCenter.Type.ERROR
                    )
                }
            }
        }
    }

    private fun applyValue(value: Value<*>, json: JsonElement) {
        when (value) {
            is OptionValue -> value.setValue(json.asBoolean)
            is NumberValue -> value.setValue(json.asDouble)
            is StringValue -> value.setValue(json.asString)
            is ChoiceValue -> applyChoice(value, json)
            is KeyValue -> value.setValue(json.asNumber.toInt())
            is ColorValue -> applyColor(value, json.asJsonObjectOrNull() ?: return)
            is ListValue -> value.setValue(parseListEntries(value, json))
            else -> throw CommandExecutionException("不支持的值类型: ${value::class.simpleName}")
        }
    }

    /** Older profiles - and Edge's `mode` settings - store the option index instead of its id. */
    private fun applyChoice(value: ChoiceValue, json: JsonElement) {
        val primitive = json.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return
        if (primitive.isNumber) {
            value.select(primitive.asInt)
        } else {
            value.setValue(primitive.asString)
        }
    }

    private fun applyColor(value: ColorValue, json: JsonObject) {
        val current = value.getValue()
        value.set(
            json.getFloatOrNull("h") ?: current.hue,
            json.getFloatOrNull("s") ?: current.saturation,
            json.getFloatOrNull("b") ?: current.brightness,
            json.getFloatOrNull("a") ?: current.alpha,
            ColorValue.Mode.of(json.getStringOrNull("mode")) ?: current.mode,
            json.getFloatOrNull("speed") ?: current.speed
        )
    }

    /** Accepts Nova's `[{text,key}]`, Edge's AutoText `[{msg,key}]`, `key:message;…`, and comma-joined ids. */
    private fun parseListEntries(value: ListValue, json: JsonElement): List<ListValue.Entry> {
        if (json.isJsonPrimitive) {
            val raw = json.asString.trim()
            if (raw.isEmpty()) {
                return emptyList()
            }
            if (value.keyed) {
                return parseKeyedListString(raw, value.capacity)
            }
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { ListValue.Entry(it) }
        }
        if (!json.isJsonArray) {
            return value.getValue()
        }
        return json.asJsonArray.mapNotNull { element ->
            if (element.isJsonPrimitive) {
                return@mapNotNull if (value.keyed) {
                    parseKeyedListString(element.asString, 1).firstOrNull()
                } else {
                    ListValue.Entry(element.asString)
                }
            }
            val entry = element.asJsonObjectOrNull() ?: return@mapNotNull null
            ListValue.Entry(
                entry.getStringOrNull("text") ?: entry.getStringOrNull("msg") ?: "",
                entry.getIntOrNull("key") ?: entry.getIntOrNull("keyCode") ?: 0
            )
        }
    }

    private fun parseKeyedListString(raw: String, capacity: Int): List<ListValue.Entry> {
        val separator = if (raw.contains(';')) ';' else ','
        return raw.split(separator).mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                return@mapNotNull null
            }
            val split = trimmed.indexOf(':')
            if (split <= 0) {
                ListValue.Entry(trimmed)
            } else {
                val keyCode = trimmed.substring(0, split).trim().toIntOrNull() ?: 0
                val text = trimmed.substring(split + 1).trim()
                if (text.isEmpty()) null else ListValue.Entry(text, keyCode)
            }
        }.take(capacity)
    }

    /**
     * Profiles written before colours became a single [ColorValue] carry `<prefix>-red/green/blue/alpha`
     * sliders. Collect the orphaned channels so they can be folded into the matching colour setting
     * instead of silently resetting every colour the player picked.
     */
    private fun collectLegacyColorChannel(
        sink: MutableMap<String, MutableMap<String, Double>>,
        identity: String,
        json: JsonElement
    ) {
        val separator = identity.lastIndexOf('-')
        val channel = identity.substring(separator + 1).lowercase(Locale.ROOT)
        if (channel !in LEGACY_COLOR_CHANNELS) {
            return
        }
        val number = runCatching { json.asDouble }.getOrNull() ?: return
        val prefix = if (separator <= 0) "" else identity.substring(0, separator)
        sink.getOrPut(prefix) { mutableMapOf() }[channel] = number
    }

    private fun applyLegacyColorChannels(
        module: top.fpsmaster.module.Module,
        channels: Map<String, Map<String, Double>>
    ) {
        channels.forEach { (prefix, values) ->
            val identity = if (prefix.isEmpty()) "color" else "$prefix-color"
            val color = module.values.firstOrNull { it.getIdentity().equals(identity, ignoreCase = true) }
                as? ColorValue ?: return@forEach
            val hsb = Color.RGBtoHSB(
                (values["red"] ?: 255.0).toInt().coerceIn(0, 255),
                (values["green"] ?: 255.0).toInt().coerceIn(0, 255),
                (values["blue"] ?: 255.0).toInt().coerceIn(0, 255),
                null
            )
            val current = color.getValue()
            color.set(hsb[0], hsb[1], hsb[2], ((values["alpha"] ?: 255.0) / 255.0).toFloat(), current.mode, current.speed)
        }
    }

    private fun applyHudComponents(components: List<ConfigHudComponent>) {
        components.forEach { entry ->
            val component = HudManager.components[entry.id] ?: return@forEach
            component.x = entry.x
            component.y = entry.y
            component.scale = entry.scale
            component.visible = entry.visible
            component.relativeX = entry.relativeX ?: Float.NaN
            component.relativeY = entry.relativeY ?: Float.NaN
        }
    }

    /** Edge profiles are converted while reading; write the Nova-format result back exactly once. */
    private fun rewriteConvertedEdgeProfile() {
        if (convertedEdgeProfile) {
            convertedEdgeProfile = false
            saveActive()
        }
    }

    /**
     * HUD placement used to live in one global `fpsmaster/hud.json`. Profiles own it now: fold the legacy
     * file into the active profile once, then drop it so there is a single write path.
     */
    private fun migrateLegacyHudFileIfNeeded() {
        val legacy = mc.gameDirectory.toPath().resolve("fpsmaster").resolve("hud.json")
        if (!legacy.exists() || !legacy.isRegularFile()) {
            return
        }
        if (!profileCarriesHudPlacement) {
            runCatching { legacy.reader().use { gson.fromJson(it, LegacyHudFile::class.java) } }
                .getOrNull()
                ?.components
                ?.let(::applyHudComponents)
            saveActive()
        }
        legacy.deleteIfExists()
    }

    /**
     * Edge writes `schemaVersion` plus a keyed `modules` object; Nova writes a `modules` array. Both
     * markers are required - a bare `modules` object is not enough to treat a file as an Edge export.
     */
    internal fun isEdgeConfig(root: JsonObject): Boolean {
        val schemaVersion = root.get("schemaVersion")
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() }
        return schemaVersion == 1 && root.get("modules")?.isJsonObject == true
    }

    var edgeImportIssues: List<EdgeImportIssue> = emptyList()
        private set

    data class EdgeImportIssue(val path: String, val reason: String)

    internal data class EdgeImport(val root: JsonObject, val issues: List<EdgeImportIssue>)

    /** Pure import boundary: never mutates the source or interprets old ids as modern registry ids. */
    internal fun prepareEdgeImport(source: JsonObject): EdgeImport {
        val root = source.deepCopy()
        val issues = mutableListOf<EdgeImportIssue>()
        fun skip(path: String, reason: String) {
            issues += EdgeImportIssue(path, reason)
        }
        fun convertKey(json: JsonObject, field: String, path: String): Boolean {
            if (!json.has(field)) return true
            val mapped = json.getExactIntOrNull(field)?.let { edgeKeyCodes[it] }
            if (mapped == null) {
                json.remove(field)
                skip(path, "unsupported-key")
                return false
            }
            json.addProperty(field, mapped)
            return true
        }
        root.getAsJsonObjectOrNull("modules")?.entrySet()?.forEach { (name, element) ->
            val module = element.asJsonObjectOrNull() ?: return@forEach
            val moduleId = edgeModuleAliases[normalizeConfigName(name)] ?: normalizeConfigName(name)
            convertKey(module, "key", "modules.$name.key")
            val settings = module.getAsJsonObjectOrNull("settings") ?: return@forEach
            val converted = JsonObject()
            settings.entrySet().forEach setting@{ (settingName, settingElement) ->
                val setting = settingElement.asJsonObjectOrNull() ?: return@setting
                val normalized = normalizeConfigName(settingName)
                val path = "modules.$name.settings.$settingName"
                if (moduleId == "optimization" && normalized in incompatibleEdgeOptimization) {
                    skip(path, "incompatible-semantics")
                    return@setting
                }
                val type = setting.getStringOrNull("type")?.lowercase(Locale.ROOT)
                var target = edgeValueAliases[moduleId to normalized] ?: normalized
                when {
                    type == "color" -> target = edgeColorIdentity(moduleId, normalized) ?: return@setting
                    moduleId == "leveltag" && normalized == "background" && type == "boolean" -> {
                        target = "disablebackground"
                        val value = setting.get("value")
                        if (value?.isJsonPrimitive != true || !value.asJsonPrimitive.isBoolean) {
                            skip(path, "invalid-boolean")
                            return@setting
                        }
                        setting.addProperty("value", !value.asBoolean)
                    }
                    type == "bind" || (type == "number" && (moduleId to target) in edgeKeySettings) ->
                        if (!convertKey(setting, "value", path)) return@setting
                    type == "autotext" -> {
                        val entries = setting.getAsJsonArrayOrNull("value")
                        if (entries == null) {
                            skip(path, "unsupported-keyed-list")
                            return@setting
                        }
                        setting.add("value", JsonArray().apply {
                            entries.forEachIndexed { index, entry ->
                                val row = entry.asJsonObjectOrNull()
                                if (row == null) {
                                    skip("$path[$index]", "invalid-keyed-entry")
                                } else {
                                    val field = if (row.has("key")) "key" else "keyCode"
                                    if (convertKey(row, field, "$path[$index].key")) add(row)
                                }
                            }
                        })
                    }
                    type == "multiitem" -> {
                        val entries = setting.getAsJsonArrayOrNull("value") ?: return@setting
                        val items = JsonArray()
                        entries.forEachIndexed { index, entry ->
                            val item = entry.asJsonObjectOrNull()
                            val id = item?.getExactIntOrNull("id")
                            val meta = item?.getExactIntOrNull("meta")
                            val mapped = if (id != null && meta != null) legacyItemIdAliases[id to meta] else null
                            if (mapped == null) skip("$path[$index]", "unsupported-item-or-metadata")
                            else items.add(mapped)
                        }
                        // Preserve the current setting if every source item was rejected.
                        if (entries.size() > 0 && items.size() == 0) return@setting
                        setting.add("value", items)
                    }
                }
                converted.add(target, setting)
            }
            module.add("settings", converted)
        }
        root.getAsJsonArrayOrNull("shortcuts")?.let { shortcuts ->
            root.add("shortcuts", JsonArray().apply {
                shortcuts.forEachIndexed { index, element ->
                    val shortcut = element.asJsonObjectOrNull() ?: return@forEachIndexed
                    if (convertKey(shortcut, "key", "shortcuts[$index].key")) add(shortcut)
                }
            })
        }
        root.getAsJsonArrayOrNull("components")?.forEach { element ->
            val component = element.asJsonObjectOrNull() ?: return@forEach
            val name = component.getStringOrNull("module") ?: return@forEach
            edgeHudComponentAliases[normalizeConfigName(name)]?.let { component.addProperty("module", it) }
        }
        return EdgeImport(root, issues.toList())
    }

    private fun loadEdgeConfig(source: JsonObject) {
        val prepared = prepareEdgeImport(source)
        val root = prepared.root
        edgeImportIssues = prepared.issues
        edgeImportIssues.forEach { logger.warn("Edge import skipped {}: {}", it.path, it.reason) }
        if (edgeImportIssues.isNotEmpty()) {
            top.fpsmaster.notification.NotificationManager.add(
                "Edge import",
                "${edgeImportIssues.size} incompatible entries skipped. See latest.log (Edge import skipped).",
                8f,
                top.fpsmaster.prism.overlay.NotificationCenter.Type.ERROR
            )
        }
        loadEdgeComponents(root)

        root.getAsJsonObjectOrNull("modules")?.entrySet()?.forEach { moduleEntry ->
            val moduleJson = moduleEntry.value.asJsonObjectOrNull() ?: return@forEach
            val module = findModuleByConfigName(moduleEntry.key) ?: return@forEach

            moduleJson.get("key")?.takeIf { it.isJsonPrimitive }?.let { module.key = it.asInt }
            moduleJson.getAsJsonObjectOrNull("settings")?.entrySet()?.forEach { settingEntry ->
                applyEdgeSetting(module, settingEntry.key, settingEntry.value.asJsonObjectOrNull() ?: return@forEach)
            }
            moduleJson.get("enabled")?.takeIf { it.isJsonPrimitive }?.let {
                if (module.persistEnabled) {
                    module.enabled = it.asBoolean
                }
            }
        }

        root.getAsJsonArrayOrNull("shortcuts")?.let { shortcuts ->
            ShortcutManager.replaceAll(
                shortcuts.mapNotNull { shortcutElement ->
                    val shortcutJson = shortcutElement.asJsonObjectOrNull() ?: return@mapNotNull null
                    val actions = shortcutJson.getAsJsonArrayOrNull("actions")
                        ?.mapNotNull { actionElement -> parseEdgeShortcutAction(actionElement.asJsonObjectOrNull()) }
                        ?.toMutableList()
                        ?: mutableListOf()

                    ShortcutManager.Shortcut(
                        name = shortcutJson.getStringOrNull("name").orEmpty(),
                        key = shortcutJson.getIntOrNull("key") ?: 0,
                        actions = actions
                    )
                }
            )
        }

        root.getAsJsonObjectOrNull("client")?.let { client ->
            applyEdgeClientPreferences(client)
            setMusicVolume(
                client.getDoubleOrNull("musicVolume")
                    ?: client.getDoubleOrNull("volume")?.let { it * 100.0 }
                    ?: musicVolume
            )
        }
    }

    fun setMusicVolume(value: Double) {
        musicVolume = value.coerceIn(0.0, 100.0)
    }

    fun setMusicPlaybackMode(mode: String) {
        musicPlaybackMode = mode
    }

    fun setClientPreferences(
        background: String,
        oobeCompleted: Boolean,
        antiCheatEnabled: Boolean,
        classicBackgroundColor: Int,
        classicBackgroundHue: Float,
        classicBackgroundSaturation: Float,
        classicBackgroundBrightness: Float,
        classicBackgroundAlpha: Float,
        classicBackgroundMode: String
    ) {
        this.background = normalizeBackground(background)
        this.oobeCompleted = oobeCompleted
        this.antiCheatEnabled = antiCheatEnabled
        this.classicBackgroundColor = classicBackgroundColor
        this.classicBackgroundHue = classicBackgroundHue.coerceIn(0f, 1f)
        this.classicBackgroundSaturation = classicBackgroundSaturation.coerceIn(0f, 1f)
        this.classicBackgroundBrightness = classicBackgroundBrightness.coerceIn(0f, 1f)
        this.classicBackgroundAlpha = classicBackgroundAlpha.coerceIn(0f, 1f)
        this.classicBackgroundMode = classicBackgroundMode.ifBlank { "STATIC" }
    }

    fun setBackground(value: String) {
        background = normalizeBackground(value)
        saveActive()
    }

    fun setClassicBackground(
        hue: Float,
        saturation: Float,
        brightness: Float,
        alpha: Float,
        mode: String
    ) {
        classicBackgroundHue = hue.coerceIn(0f, 1f)
        classicBackgroundSaturation = saturation.coerceIn(0f, 1f)
        classicBackgroundBrightness = brightness.coerceIn(0f, 1f)
        classicBackgroundAlpha = alpha.coerceIn(0f, 1f)
        classicBackgroundMode = mode.ifBlank { "STATIC" }
        val rgb = java.awt.Color.HSBtoRGB(classicBackgroundHue, classicBackgroundSaturation, classicBackgroundBrightness) and 0x00FFFFFF
        classicBackgroundColor = ((classicBackgroundAlpha * 255f).toInt() shl 24) or rgb
    }

    private fun applyClientPreferences(client: ConfigClient) {
        setClientPreferences(
            background = client.background,
            oobeCompleted = client.oobeCompleted,
            antiCheatEnabled = client.antiCheatEnabled,
            classicBackgroundColor = client.classicBackgroundColor,
            classicBackgroundHue = client.classicBackgroundHue,
            classicBackgroundSaturation = client.classicBackgroundSaturation,
            classicBackgroundBrightness = client.classicBackgroundBrightness,
            classicBackgroundAlpha = client.classicBackgroundAlpha,
            classicBackgroundMode = client.classicBackgroundMode
        )
        CosmeticManager.configure(
            capeId = client.cosmeticCapeId,
            wingsId = client.cosmeticWingsId,
            wingsEnabled = client.cosmeticWingsEnabled,
            capeAnimationEnabled = client.capeAnimationEnabled,
            wingScale = client.cosmeticWingScale
        )
    }

    private fun applyEdgeClientPreferences(client: JsonObject) {
        val edgeColor = client.getIntOrNull("classicBackgroundColor") ?: classicBackgroundColor
        val hsb = FloatArray(3)
        Color.RGBtoHSB((edgeColor shr 16) and 0xFF, (edgeColor shr 8) and 0xFF, edgeColor and 0xFF, hsb)
        setClientPreferences(
            background = client.getStringOrNull("background") ?: background,
            oobeCompleted = client.getBooleanOrNull("oobeCompleted") ?: oobeCompleted,
            antiCheatEnabled = client.getBooleanOrNull("antiCheatEnabled") ?: true,
            classicBackgroundColor = edgeColor,
            classicBackgroundHue = client.getFloatOrNull("classicBackgroundHue") ?: hsb[0],
            classicBackgroundSaturation = client.getFloatOrNull("classicBackgroundSaturation") ?: hsb[1],
            classicBackgroundBrightness = client.getFloatOrNull("classicBackgroundBrightness") ?: hsb[2],
            classicBackgroundAlpha = client.getFloatOrNull("classicBackgroundAlpha") ?: ((edgeColor ushr 24) / 255f),
            classicBackgroundMode = client.getStringOrNull("classicBackgroundMode") ?: classicBackgroundMode
        )
    }

    private fun normalizeBackground(value: String): String {
        return if (value == "new" || value.isBlank()) "panorama_1" else value
    }

    private fun loadEdgeComponents(root: JsonObject) {
        root.getAsJsonArrayOrNull("components")?.forEach { element ->
            val componentJson = element.asJsonObjectOrNull() ?: return@forEach
            val moduleName = componentJson.getStringOrNull("module") ?: return@forEach
            val component = HudManager.components[moduleName]
                ?: return@forEach

            component.scale = componentJson.getFloatOrNull("scale") ?: component.scale
            val edgeX = componentJson.getFloatOrNull("x") ?: return@forEach
            val edgeY = componentJson.getFloatOrNull("y") ?: return@forEach
            val position = componentJson.getStringOrNull("position") ?: "LT"
            val size = component.measure(preview = true)
            val width = mc.window.guiScaledWidth.toFloat().coerceAtLeast(1f)
            val height = mc.window.guiScaledHeight.toFloat().coerceAtLeast(1f)
            val offsetX = edgeX.coerceIn(0f, 1f) * width / 2f
            val offsetY = edgeY.coerceIn(0f, 1f) * height / 2f
            val componentWidth = size.width * component.scale
            val componentHeight = size.height * component.scale

            when (position.uppercase(Locale.ROOT)) {
                "RT" -> {
                    component.x = width - offsetX - componentWidth
                    component.y = offsetY
                }

                "LB" -> {
                    component.x = offsetX
                    component.y = height - offsetY - componentHeight
                }

                "RB" -> {
                    component.x = width - offsetX - componentWidth
                    component.y = height - offsetY - componentHeight
                }

                "CT" -> {
                    component.x = width / 2f - componentWidth / 2f
                    component.y = offsetY
                }

                else -> {
                    component.x = offsetX
                    component.y = offsetY
                }
            }

            component.x = component.x.coerceIn(0f, (width - componentWidth).coerceAtLeast(0f))
            component.y = component.y.coerceIn(0f, (height - componentHeight).coerceAtLeast(0f))
            component.visible = true
        }
        profileCarriesHudPlacement = root.get("components")?.isJsonArray == true
    }

    private fun applyEdgeSetting(module: top.fpsmaster.module.Module, settingName: String, settingJson: JsonObject) {
        val type = settingJson.getStringOrNull("type")?.lowercase(Locale.ROOT) ?: return
        val rawValue = settingJson.get("value") ?: return

        if (type == "color") {
            applyEdgeColor(module, settingName, rawValue.asJsonObjectOrNull() ?: return)
            return
        }

        if (type == "multiitem") {
            val target = findValueByConfigName(module, settingName) as? ListValue ?: return
            target.setValue(parseListEntries(target, rawValue))
            return
        }

        val value = findValueByConfigName(module, settingName) ?: return
        when (value) {
            is OptionValue -> if (type == "boolean") value.setValue(rawValue.asBoolean)
            is NumberValue -> if (type == "number" || type == "mode" || type == "bind") value.setValue(rawValue.asDouble)
            is StringValue -> if (type == "text") runCatching { value.setValue(rawValue.asString) }
            is ChoiceValue -> when (type) {
                "mode" -> value.select(rawValue.asInt)
                "text" -> value.setValue(rawValue.asString)
                else -> Unit
            }
            is KeyValue -> if (type == "bind" || type == "number") value.setValue(rawValue.asNumber.toInt())
            is ListValue -> if (type == "autotext") value.setValue(parseListEntries(value, rawValue))
            else -> Unit
        }
    }

    private fun applyEdgeColor(module: top.fpsmaster.module.Module, settingName: String, colorJson: JsonObject) {
        val color = module.values.firstOrNull { it.getIdentity() == settingName } as? ColorValue ?: return
        val current = color.getValue()
        color.set(
            colorJson.getFloatOrNull("h") ?: current.hue,
            colorJson.getFloatOrNull("s") ?: current.saturation,
            colorJson.getFloatOrNull("b") ?: current.brightness,
            colorJson.getFloatOrNull("a") ?: current.alpha,
            ColorValue.Mode.of(colorJson.getStringOrNull("mode")) ?: current.mode,
            colorJson.getFloatOrNull("speed") ?: current.speed
        )
    }

    /** Maps an Edge `ColorSetting` name onto the Nova [ColorValue] identity that replaced it. */
    private fun edgeColorIdentity(moduleId: String, settingName: String): String? {
        val prefix = if (settingName == "backgroundcolor") {
            "background"
        } else {
            edgeColorPrefixes[moduleId to settingName] ?: return null
        }
        return if (prefix.isEmpty()) "color" else "$prefix-color"
    }

    private fun findModuleByConfigName(name: String): top.fpsmaster.module.Module? {
        val moduleId = edgeModuleAliases[normalizeConfigName(name)] ?: normalizeConfigName(name)
        return ModuleManager.modules.values.firstOrNull { normalizeConfigName(it.identity) == moduleId }
    }

    private fun findValueByConfigName(module: top.fpsmaster.module.Module, name: String): Value<*>? {
        val normalizedModule = normalizeConfigName(module.identity)
        val normalizedName = edgeValueAliases[normalizedModule to normalizeConfigName(name)] ?: normalizeConfigName(name)
        return module.values.firstOrNull { normalizeConfigName(it.getIdentity()) == normalizedName }
    }

    private fun parseEdgeShortcutAction(actionJson: JsonObject?): ShortcutManager.Action? {
        if (actionJson == null) {
            return null
        }

        val typeName = actionJson.getStringOrNull("type") ?: return null
        val type = runCatching { ShortcutManager.ActionType.valueOf(typeName.uppercase(Locale.ROOT)) }.getOrNull()
            ?: return null
        val context = actionJson.getStringOrNull("context")
            ?: actionJson.getStringOrNull("Context")
            ?: ""
        return ShortcutManager.Action(type = type, context = context)
    }

    private fun normalizeConfigName(name: String): String {
        return name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun JsonObject.getExactIntOrNull(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.let { runCatching { it.asBigDecimal.intValueExact() }.getOrNull() }

    private fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? {
        return get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.getAsJsonArrayOrNull(name: String): com.google.gson.JsonArray? {
        return get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    }

    private fun JsonObject.getStringOrNull(name: String): String? {
        return get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun JsonObject.getBooleanOrNull(name: String): Boolean? {
        return get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean
    }

    private fun JsonObject.getIntOrNull(name: String): Int? {
        return get(name)?.takeIf { it.isJsonPrimitive }?.asInt
    }

    private fun JsonObject.getFloatOrNull(name: String): Float? {
        return get(name)?.takeIf { it.isJsonPrimitive }?.asFloat
    }

    private fun JsonObject.getDoubleOrNull(name: String): Double? {
        return get(name)?.takeIf { it.isJsonPrimitive }?.asDouble
    }

    private fun toJson(value: Value<*>): JsonElement {
        return when (value) {
            is OptionValue -> gson.toJsonTree(value.getValue())
            is NumberValue -> gson.toJsonTree(value.getValue())
            is StringValue -> gson.toJsonTree(value.getValue())
            is ChoiceValue -> gson.toJsonTree(value.getValue())
            is KeyValue -> gson.toJsonTree(value.getValue())
            is ColorValue -> colorToJson(value)
            is ListValue -> listToJson(value)
            else -> throw CommandExecutionException("不支持的值类型: ${value::class.simpleName}")
        }
    }

    /** Same field names Edge writes, so a colour survives a round trip through either client. */
    private fun colorToJson(value: ColorValue): JsonElement {
        val snapshot = value.getValue()
        return buildJsonObject {
            addProperty("h", snapshot.hue)
            addProperty("s", snapshot.saturation)
            addProperty("b", snapshot.brightness)
            addProperty("a", snapshot.alpha)
            addProperty("mode", snapshot.mode.id)
            addProperty("speed", snapshot.speed)
        }
    }

    private fun listToJson(value: ListValue): JsonElement {
        val array = JsonArray()
        value.getValue().forEach { entry ->
            array.add(buildJsonObject {
                addProperty("text", entry.text)
                addProperty("key", entry.keyCode)
            })
        }
        return array
    }

    private fun configPath(name: String): Path {
        val safeName = sanitizeName(name)
        return ensureConfigDirectory().resolve("$safeName.json")
    }

    private fun sanitizeName(name: String): String {
        val safeName = name.trim().removeSuffix(".json")
        if (!validNameRegex.matches(safeName)) {
            throw CommandExecutionException("配置名只能包含字母、数字、点、下划线和短横线")
        }
        return safeName
    }

    private fun ensureConfigDirectory(): Path {
        val directory = mc.gameDirectory.toPath()
            .resolve("fpsmaster")
            .resolve("configs")
        Files.createDirectories(directory)
        return directory
    }

    private fun activeProfileStatePath(): Path {
        return ensureConfigDirectory().resolve(ACTIVE_PROFILE_STATE)
    }

    private fun loadActiveProfileName(): String {
        val statePath = activeProfileStatePath()
        if (!statePath.exists() || !statePath.isRegularFile()) {
            activeProfileName = DEFAULT_CONFIG_NAME
            return activeProfileName
        }

        val profileName = runCatching {
            sanitizeName(statePath.reader().use { it.readText() })
        }.getOrDefault(DEFAULT_CONFIG_NAME)
        activeProfileName = if (configPath(profileName).exists()) profileName else DEFAULT_CONFIG_NAME
        if (activeProfileName != profileName) {
            saveActiveProfileName()
        }
        return activeProfileName
    }

    private fun saveActiveProfileName() {
        synchronized(saveLock) {
            AtomicFiles.write(activeProfileStatePath(), activeProfileName)
        }
    }

    private fun backupInvalidConfig(path: Path): Path {
        val backupPath = path.resolveSibling("${path.fileName}.${System.currentTimeMillis()}.invalid")
        Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING)
        return backupPath
    }

    private fun migrateLegacyDefaultIfNeeded() {
        val legacyPath = mc.gameDirectory.toPath()
            .resolve("fpsmaster")
            .resolve("$DEFAULT_CONFIG_NAME.json")
        val targetPath = configPath(DEFAULT_CONFIG_NAME)
        if (!legacyPath.exists() || targetPath.exists()) {
            return
        }

        Files.createDirectories(targetPath.parent)
        Files.move(legacyPath, targetPath)
    }

    private fun migrateEdgeProfilesIfNeeded() {
        val legacyDirectory = mc.gameDirectory.toPath()
            .resolve("fpsmaster")
            .resolve("config")
        if (!legacyDirectory.exists()) {
            return
        }

        val targetDirectory = ensureConfigDirectory()
        legacyDirectory.listDirectoryEntries("*.json").forEach { source ->
            val target = targetDirectory.resolve(source.fileName)
            if (!target.exists()) {
                Files.copy(source, target)
            }
        }

        val legacyActiveProfile = legacyDirectory.resolve(ACTIVE_PROFILE_STATE)
        val targetActiveProfile = activeProfileStatePath()
        if (legacyActiveProfile.exists() && !targetActiveProfile.exists()) {
            Files.copy(legacyActiveProfile, targetActiveProfile)
        }
    }

    private inline fun buildJsonObject(builder: JsonObject.() -> Unit): JsonObject {
        return JsonObject().apply(builder)
    }

    private data class ConfigFile(
        val profile: ConfigProfile = ConfigProfile(),
        val modules: List<ConfigModule> = emptyList(),
        val shortcuts: List<ConfigShortcut> = emptyList(),
        val client: ConfigClient? = ConfigClient(),
        /** Null means the profile predates profile-owned HUD placement; see migrateLegacyHudFileIfNeeded. */
        val hud: List<ConfigHudComponent>? = null,
        val musicVolume: Double? = null,
        val volume: Double? = null
    )

    private data class ConfigHudComponent(
        val id: String = "",
        val x: Float = 0f,
        val y: Float = 0f,
        val scale: Float = 1f,
        val visible: Boolean = true,
        val relativeX: Float? = null,
        val relativeY: Float? = null
    )

    private data class LegacyHudFile(
        val components: List<ConfigHudComponent> = emptyList()
    )

    private data class ConfigProfile(
        val name: String = "",
        val author: String = ""
    )

    private data class ConfigModule(
        val id: String = "",
        val enabled: Boolean = false,
        val key: Int = 0,
        val values: JsonObject = JsonObject()
    )

    private data class ConfigClient(
        val musicVolume: Double = 75.0,
        val volume: Double = musicVolume / 100.0,
        val musicPlaybackMode: String = "SEQUENTIAL",
        val background: String = "panorama_1",
        val oobeCompleted: Boolean = false,
        val antiCheatEnabled: Boolean = true,
        val classicBackgroundColor: Int = 0xFF000000.toInt(),
        val classicBackgroundHue: Float = 0f,
        val classicBackgroundSaturation: Float = 0f,
        val classicBackgroundBrightness: Float = 0f,
        val classicBackgroundAlpha: Float = 1f,
        val classicBackgroundMode: String = "STATIC",
        val cosmeticCapeId: String? = null,
        val cosmeticWingsId: String = "builtin:dragon-wings",
        val cosmeticWingsEnabled: Boolean = false,
        val capeAnimationEnabled: Boolean = false,
        val cosmeticWingScale: Float = 1f
    )

    private data class ConfigShortcut(
        val name: String = "",
        val key: Int = 0,
        val actions: List<ConfigShortcutAction> = emptyList()
    )

    private data class ConfigShortcutAction(
        val type: ShortcutManager.ActionType = ShortcutManager.ActionType.SEND_MESSAGE,
        val context: String = ""
    )

    private val LEGACY_COLOR_CHANNELS = setOf("red", "green", "blue", "alpha")

    /**
     * Edge module + `ColorSetting` name -> the Nova [ColorValue] identity prefix.
     * An empty prefix means the module owns the bare `color` setting.
     */
    private val edgeColorPrefixes = mapOf(
        ("blockoverlay" to "fillcolor") to "fill",
        ("blockoverlay" to "outlinecolor") to "outline",
        ("blockoverlay" to "color") to "outline",
        ("blockindicator" to "panelcolor") to "panel",
        ("blockindicator" to "accentcolor") to "accent",
        ("fpsdisplay" to "textcolor") to "text",
        ("cpsdisplay" to "textcolor") to "text",
        ("pingdisplay" to "textcolor") to "text",
        ("reachdisplay" to "textcolor") to "text",
        ("combodisplay" to "textcolor") to "text",
        ("clockdisplay" to "textcolor") to "text",
        ("serveraddressdisplay" to "textcolor") to "text",
        ("playtime" to "textcolor") to "text",
        ("performancehud" to "textcolor") to "text",
        ("saturationdisplay" to "barcolor") to "bar",
        ("customfog" to "color") to "",
        ("lyricsdisplay" to "textcolor") to "text",
        ("lyricsdisplay" to "panelcolor") to "panel",
        ("modslist" to "color") to "color",
        ("hitcolor" to "color") to "",
        ("hitboxes" to "color") to "",
        ("firemodifier" to "color") to "",
        ("crosshair" to "color") to "color",
        ("crosshair" to "outlinecolor") to "outline",
        ("crosshair" to "enemy") to "enemy",
        ("crosshair" to "friend") to "friend",
        ("targetdisplay" to "espcolor") to "esp",
        ("keystrokes" to "pressedcolor") to "pressed",
        ("keystrokes" to "fontcolor") to "font",
        ("keystrokes" to "pressedfontcolor") to "pressed-font",
        ("keystrokes" to "bordercolor") to "border",
        ("keystrokes" to "pressanimcolor") to "press-anim"
    )

    private val edgeModuleAliases = mapOf(
        "oldanimations" to "animation",
        "performance" to "optimization",
        "clientsettings" to "clientsettings",
        "nametags" to "leveltag"
    )

    private val edgeHudComponentAliases = mapOf(
        "fpsdisplay" to "fps_text",
        "armordisplay" to "armor",
        "scoreboard" to "scoreboard",
        "potiondisplay" to "potion_text",
        "cpsdisplay" to "cps_text",
        "keystrokes" to "keystrokes",
        "reachdisplay" to "reach_text",
        "combodisplay" to "combo_text",
        "inventorydisplay" to "inventory",
        "targetdisplay" to "target_hud",
        "playerdisplay" to "player_display",
        "pingdisplay" to "ping_text",
        "coordsdisplay" to "coords_text",
        "modslist" to "mods_list",
        "minimap" to "mini_map",
        "directiondisplay" to "direction_text",
        "sprint" to "sprint_text",
        "blockindicator" to "block_indicator",
        "itemcountdisplay" to "item_count",
        "clockdisplay" to "clock",
        "performancehud" to "performance",
        "playtime" to "play_time",
        "saturationdisplay" to "saturation",
        "serveraddressdisplay" to "server_address",
        "lyricsdisplay" to "lyrics",
        "tnttimerhud" to "tnt_timer",
        "damageindicatorhud" to "damage_indicator"
    )

    // Explicit (1.8 numeric id, metadata) pairs only. Potions require components that this list lacks.
    private val legacyItemIdAliases = mapOf(
        (262 to 0) to "minecraft:arrow",
        (322 to 0) to "minecraft:golden_apple",
        (322 to 1) to "minecraft:enchanted_golden_apple",
        (368 to 0) to "minecraft:ender_pearl"
    )

    private val incompatibleEdgeOptimization = setOf(
        "ignorestands", "ignorearmorstand", "entitiesoptimize", "entityculling",
        "particleslimit", "particlelimitation"
    )

    private val edgeKeySettings = setOf(
        "clientsettings" to "clickguikey", "clientsettings" to "zoombind",
        "smoothzoom" to "zoombind", "freelook" to "bind", "replay" to "clipkey"
    )

    // LWJGL2 scan-code constants -> GLFW physical-key constants (not characters).
    // Nova has no mouse binding channel, so negative/mouse and unknown codes are deliberately absent.
    private val edgeKeyCodes = mapOf(
        0 to 0, 1 to 256, // unbound, Escape
        2 to 49, 3 to 50, 4 to 51, 5 to 52, 6 to 53, 7 to 54, 8 to 55, 9 to 56, 10 to 57, 11 to 48,
        12 to 45, 13 to 61, 14 to 259, 15 to 258, // minus, equals, Backspace, Tab
        16 to 81, 17 to 87, 18 to 69, 19 to 82, 20 to 84, 21 to 89, 22 to 85, 23 to 73, 24 to 79, 25 to 80,
        26 to 91, 27 to 93, 28 to 257, 29 to 341, // brackets, Enter, left Ctrl
        30 to 65, 31 to 83, 32 to 68, 33 to 70, 34 to 71, 35 to 72, 36 to 74, 37 to 75, 38 to 76,
        39 to 59, 40 to 39, 41 to 96, 42 to 340, 43 to 92, // punctuation, left Shift, backslash
        44 to 90, 45 to 88, 46 to 67, 47 to 86, 48 to 66, 49 to 78, 50 to 77,
        51 to 44, 52 to 46, 53 to 47, 54 to 344, 55 to 332, 56 to 342, 57 to 32, 58 to 280,
        59 to 290, 60 to 291, 61 to 292, 62 to 293, 63 to 294, 64 to 295, 65 to 296, 66 to 297,
        67 to 298, 68 to 299, 69 to 282, 70 to 281, // F1-F10, Num Lock, Scroll Lock
        71 to 327, 72 to 328, 73 to 329, 74 to 333, 75 to 324, 76 to 325, 77 to 326, 78 to 334,
        79 to 321, 80 to 322, 81 to 323, 82 to 320, 83 to 330, // keypad
        87 to 300, 88 to 301, 100 to 302, 101 to 303, 102 to 304, 103 to 305, 104 to 306,
        105 to 307, 113 to 308, 141 to 336, 156 to 335, 157 to 345, // F11-F19, keypad =/Enter, right Ctrl
        181 to 331, 183 to 283, 184 to 346, 197 to 284, // keypad /, Print Screen, right Alt, Pause
        199 to 268, 200 to 265, 201 to 266, 203 to 263, 205 to 262, 207 to 269, 208 to 264,
        209 to 267, 210 to 260, 211 to 261, 219 to 343, 220 to 347, 221 to 348 // navigation, Super, Menu
    )

    private val edgeValueAliases = mapOf(
        "optimization" to "entitylimit" to "entitylimitation",
        "optimization" to "fpslimit" to "fpslosingfocus",
        "optimization" to "limitchunks" to "chunkloadinglimitation",
        "optimization" to "chunkupdatelimit" to "chunkupdatinglimitation",
        "smoothzoom" to "smoothzoom" to "smoothcamera",
        "smoothzoom" to "wheelzoom" to "wheelzoom",
        "smoothzoom" to "zoombind" to "zoombind",
        "clientsettings" to "command" to "clientcommand",
        "clientsettings" to "prefix" to "commandprefix",
        "clientsettings" to "language" to "language",
        "clientsettings" to "blur" to "blur",
        "clientsettings" to "theme" to "theme",
        "clientsettings" to "clickguikey" to "clickguikey",
        "clientsettings" to "fixedscaleenabled" to "fixedscaleenabled",
        "clientsettings" to "fixedscale" to "fixedscale",
        "clientsettings" to "zoombind" to "zoombind",
        "freelook" to "bind" to "bind",
        "autogg" to "servers" to "servermode",
        "moreparticles" to "killeffect" to "killeffect",
        "scoreboard" to "score" to "score",
        "targetdisplay" to "targetesp" to "targetesp",
        "targetdisplay" to "targethud" to "targethud",
        "targetdisplay" to "omitname" to "omitname",
        "itemcountdisplay" to "mode" to "mode",
        "customfov" to "nospeedfov" to "nospeedfov",
        "customfov" to "noflyfov" to "noflyfov",
        "customfov" to "nobowfov" to "nobowfov",
        "nameprotect" to "name" to "replacement",
        "keystrokes" to "borderwidth" to "borderwidth",
        "keystrokes" to "pressanimmode" to "pressanimmode",
        "keystrokes" to "pressanimduration" to "pressanimduration",
        "keystrokes" to "showspace" to "showspace",
        "keystrokes" to "cpsmode" to "cpsmode",
        "keystrokes" to "wasdstyle" to "wasdstyle",
        "keystrokes" to "spacestyle" to "spacestyle"
    )
}
