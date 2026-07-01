package top.fpsmaster.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import top.fpsmaster.logger
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.hud.HudConfigManager
import top.fpsmaster.hud.HudManager
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.value.Value
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.shortcut.ShortcutManager
import top.fpsmaster.telemetry.TelemetryReporter
import top.fpsmaster.web.network.packets.PacketRegistryInitializer
import top.fpsmaster.mc
import java.awt.Color
//? if >=1.20 {
import net.minecraft.core.registries.BuiltInRegistries
//?} else {
/*import net.minecraft.core.Registry as BuiltInRegistries*/
//?}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Locale
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.reader

object ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val saveLock = Any()
    private val validNameRegex = Regex("^[a-zA-Z0-9._-]+$")
    private const val DEFAULT_CONFIG_NAME = "default"
    private const val ACTIVE_PROFILE_STATE = "active_profile.txt"
    private var activeProfileName = DEFAULT_CONFIG_NAME
    var musicVolume: Double = 75.0
        private set
    var background: String = "panorama_1"
        private set
    var oobeCompleted: Boolean = false
        private set
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
            } catch (exception: CommandExecutionException) {
                val backupPath = backupInvalidConfig(path)
                logger.warn("Failed to load config '{}'; backed it up to {}", profileName, backupPath.fileName, exception)
                saveDefault()
            }
        } else {
            saveDefault()
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
    }

    fun load(name: String) {
        val path = configPath(name)
        if (!path.exists() || !path.isRegularFile()) {
            throw CommandExecutionException("配置不存在: $name")
        }

        val root = try {
            val json = path.reader().use { JsonParser.parseReader(it) }
            json?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (exception: IllegalStateException) {
            throw CommandExecutionException("配置文件无效: $name")
        } catch (exception: JsonParseException) {
            throw CommandExecutionException("配置文件无效: $name")
        } ?: throw CommandExecutionException("配置文件无效: $name")

        if (isEdgeConfig(root)) {
            loadEdgeConfig(root)
            PacketRegistryInitializer.broadcastModuleSnapshot()
            return
        }

        val config = gson.fromJson(root, ConfigFile::class.java)
            ?: throw CommandExecutionException("配置文件无效: $name")

        config.modules.forEach { moduleEntry ->
            val module = ModuleManager.modules[moduleEntry.id.lowercase()] ?: return@forEach
            module.key = moduleEntry.key
            moduleEntry.values.entrySet().forEach { valueEntry ->
                val value = module.values.firstOrNull { it.getIdentity().equals(valueEntry.key, ignoreCase = true) }
                    ?: return@forEach
                applyValue(value, valueEntry.value)
            }
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
        TelemetryReporter.configure(
            enabled = config.client?.anonymousDataEnabled ?: config.anonymousDataEnabled,
            instanceId = config.client?.telemetryInstanceId ?: config.telemetryInstanceId
        )
        config.client?.let(::applyClientPreferences)
        setMusicVolume(
            config.client?.musicVolume
                ?: config.client?.volume?.let { it * 100.0 }
                ?: config.musicVolume
                ?: config.volume?.let { it * 100.0 }
                ?: musicVolume
        )

        PacketRegistryInitializer.broadcastModuleSnapshot()
        PacketRegistryInitializer.broadcastClientConfig()
    }

    fun delete(name: String) {
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
        Files.copy(sourcePath, configPath(profileName), StandardCopyOption.REPLACE_EXISTING)
        return profileName
    }

    fun exportActive(target: String) {
        saveActive()
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
        PacketRegistryInitializer.broadcastModuleSnapshot()
    }

    fun listNames(prefix: String = ""): List<String> {
        val directory = ensureConfigDirectory()
        return directory.listDirectoryEntries("*.json")
            .map { it.fileName.toString().removeSuffix(".json") }
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .sorted()
    }

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
                anonymousDataEnabled = TelemetryReporter.anonymousDataEnabled,
                telemetryInstanceId = TelemetryReporter.telemetryInstanceId,
                musicVolume = musicVolume,
                volume = musicVolume / 100.0,
                background = background,
                oobeCompleted = oobeCompleted,
                antiCheatEnabled = antiCheatEnabled,
                classicBackgroundColor = classicBackgroundColor,
                classicBackgroundHue = classicBackgroundHue,
                classicBackgroundSaturation = classicBackgroundSaturation,
                classicBackgroundBrightness = classicBackgroundBrightness,
                classicBackgroundAlpha = classicBackgroundAlpha,
                classicBackgroundMode = classicBackgroundMode
            )
        )

        synchronized(saveLock) {
            writeJsonAtomically(path, config)
        }
    }

    private fun applyValue(value: Value<*>, json: JsonElement) {
        when (value) {
            is OptionValue -> value.setValue(json.asBoolean)
            is NumberValue -> value.setValue(json.asDouble)
            is StringValue -> value.setValue(json.asString)
            else -> throw CommandExecutionException("不支持的值类型: ${value::class.simpleName}")
        }
    }

    private fun isEdgeConfig(root: JsonObject): Boolean {
        val modules = root.get("modules")
        return modules != null && modules.isJsonObject
    }

    private fun loadEdgeConfig(root: JsonObject) {
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
            TelemetryReporter.configure(
                enabled = client.getBooleanOrNull("anonymousDataEnabled") ?: false,
                instanceId = client.getStringOrNull("telemetryInstanceId")
            )
            applyEdgeClientPreferences(client)
            setMusicVolume(
                client.getDoubleOrNull("musicVolume")
                    ?: client.getDoubleOrNull("volume")?.let { it * 100.0 }
                    ?: musicVolume
            )
        }
        PacketRegistryInitializer.broadcastClientConfig()
    }

    fun setMusicVolume(value: Double) {
        musicVolume = value.coerceIn(0.0, 100.0)
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
            val component = HudManager.components[edgeHudComponentAliases[normalizeConfigName(moduleName)]]
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
        HudConfigManager.save()
    }

    private fun applyEdgeSetting(module: top.fpsmaster.module.Module, settingName: String, settingJson: JsonObject) {
        val type = settingJson.getStringOrNull("type")?.lowercase(Locale.ROOT) ?: return
        val rawValue = settingJson.get("value") ?: return

        if (type == "color") {
            applyEdgeColor(module, settingName, rawValue.asJsonObjectOrNull() ?: return)
            return
        }

        if (type == "multiitem") {
            applyEdgeMultiItem(module, settingName, rawValue)
            return
        }

        val value = findValueByConfigName(module, settingName) ?: return
        when (value) {
            is OptionValue -> if (type == "boolean") value.setValue(rawValue.asBoolean)
            is NumberValue -> if (type == "number" || type == "mode" || type == "bind") value.setValue(rawValue.asDouble)
            is StringValue -> if (type == "text") runCatching { value.setValue(rawValue.asString) }
            else -> Unit
        }
    }

    private fun applyEdgeMultiItem(module: top.fpsmaster.module.Module, settingName: String, rawValue: JsonElement) {
        if (normalizeConfigName(module.identity) != "itemcountdisplay" || normalizeConfigName(settingName) != "items") {
            return
        }
        val target = findValueByConfigName(module, settingName) as? StringValue ?: return
        if (!rawValue.isJsonArray) {
            return
        }

        val itemIds = rawValue.asJsonArray.mapNotNull { element ->
            val item = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val legacyId = item.getIntOrNull("id") ?: return@mapNotNull null
            legacyItemIdAliases[legacyId] ?: BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.byId(legacyId)).toString()
        }
        if (itemIds.isNotEmpty()) {
            target.setValue(itemIds.distinct().joinToString(","))
        }
    }

    private fun applyEdgeColor(module: top.fpsmaster.module.Module, settingName: String, colorJson: JsonObject) {
        val h = colorJson.getFloatOrNull("h") ?: return
        val s = colorJson.getFloatOrNull("s") ?: return
        val b = colorJson.getFloatOrNull("b") ?: return
        val a = colorJson.getFloatOrNull("a") ?: return
        val rgb = Color(Color.HSBtoRGB(h, s, b))
        val channels = edgeColorChannels(module.identity, settingName) ?: return

        setNumberValue(module, channels.red, rgb.red.toDouble())
        setNumberValue(module, channels.green, rgb.green.toDouble())
        setNumberValue(module, channels.blue, rgb.blue.toDouble())
        channels.alpha?.let { setNumberValue(module, it, (a * 255.0f).toDouble()) }
    }

    private fun edgeColorChannels(moduleId: String, settingName: String): ColorChannels? {
        if (normalizeConfigName(settingName) == "backgroundcolor") {
            return ColorChannels("background-red", "background-green", "background-blue", "background-alpha")
        }

        return when (normalizeConfigName(moduleId) to normalizeConfigName(settingName)) {
            "blockoverlay" to "fillcolor" -> ColorChannels("fill-red", "fill-green", "fill-blue", "fill-alpha")
            "blockoverlay" to "outlinecolor" -> ColorChannels("outline-red", "outline-green", "outline-blue", "outline-alpha")
            "blockoverlay" to "color" -> ColorChannels("outline-red", "outline-green", "outline-blue", "outline-alpha")
            "blockindicator" to "panelcolor" -> ColorChannels("panel-red", "panel-green", "panel-blue", "panel-alpha")
            "blockindicator" to "accentcolor" -> ColorChannels("accent-red", "accent-green", "accent-blue", "accent-alpha")
            "fpsdisplay" to "textcolor" -> ColorChannels("text-red", "text-green", "text-blue", "text-alpha")
            "cpsdisplay" to "textcolor" -> ColorChannels("text-red", "text-green", "text-blue", "text-alpha")
            "pingdisplay" to "textcolor" -> ColorChannels("text-red", "text-green", "text-blue", "text-alpha")
            "reachdisplay" to "textcolor" -> ColorChannels("text-red", "text-green", "text-blue", "text-alpha")
            "combodisplay" to "textcolor" -> ColorChannels("text-red", "text-green", "text-blue", "text-alpha")
            "modslist" to "color" -> ColorChannels("color-red", "color-green", "color-blue", "color-alpha")
            "hitcolor" to "color" -> ColorChannels("red", "green", "blue", "alpha")
            "hitboxes" to "color" -> ColorChannels("red", "green", "blue", "alpha")
            "firemodifier" to "color" -> ColorChannels("red", "green", "blue", null)
            "crosshair" to "color" -> ColorChannels("color-red", "color-green", "color-blue", "color-alpha")
            "crosshair" to "outlinecolor" -> ColorChannels("outline-red", "outline-green", "outline-blue", "outline-alpha")
            "crosshair" to "enemy" -> ColorChannels("enemy-red", "enemy-green", "enemy-blue", "enemy-alpha")
            "crosshair" to "friend" -> ColorChannels("friend-red", "friend-green", "friend-blue", "friend-alpha")
            "dragonwings" to "color" -> ColorChannels("color-red", "color-green", "color-blue", "color-alpha")
            "targetdisplay" to "espcolor" -> ColorChannels("esp-red", "esp-green", "esp-blue", "esp-alpha")
            "keystrokes" to "pressedcolor" -> ColorChannels("pressed-red", "pressed-green", "pressed-blue", "pressed-alpha")
            "keystrokes" to "fontcolor" -> ColorChannels("font-red", "font-green", "font-blue", "font-alpha")
            "keystrokes" to "pressedfontcolor" -> ColorChannels("pressed-font-red", "pressed-font-green", "pressed-font-blue", "pressed-font-alpha")
            "keystrokes" to "bordercolor" -> ColorChannels("border-red", "border-green", "border-blue", "border-alpha")
            "keystrokes" to "pressanimcolor" -> ColorChannels("press-anim-red", "press-anim-green", "press-anim-blue", "press-anim-alpha")
            else -> null
        }
    }

    private fun setNumberValue(module: top.fpsmaster.module.Module, name: String, value: Double) {
        (module.values.firstOrNull { it.getIdentity().equals(name, ignoreCase = true) } as? NumberValue)
            ?.setValue(value)
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
            else -> throw CommandExecutionException("不支持的值类型: ${value::class.simpleName}")
        }
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
            writeTextAtomically(activeProfileStatePath(), activeProfileName)
        }
    }

    private fun backupInvalidConfig(path: Path): Path {
        val backupPath = path.resolveSibling("${path.fileName}.${System.currentTimeMillis()}.invalid")
        Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING)
        return backupPath
    }

    private fun writeJsonAtomically(path: Path, value: Any) {
        writeTextAtomically(path, gson.toJson(value))
    }

    private fun writeTextAtomically(path: Path, content: String) {
        Files.createDirectories(path.parent)
        val tempPath = path.resolveSibling("${path.fileName}.${System.nanoTime()}.tmp")
        try {
            FileChannel.open(
                tempPath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            ).use { channel ->
                val buffer = ByteBuffer.wrap(content.toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }
            Files.move(
                tempPath,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (exception: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tempPath.deleteIfExists()
        }
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
        val anonymousDataEnabled: Boolean = false,
        val telemetryInstanceId: String = "",
        val musicVolume: Double? = null,
        val volume: Double? = null
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
        val anonymousDataEnabled: Boolean = false,
        val telemetryInstanceId: String = "",
        val musicVolume: Double = 75.0,
        val volume: Double = musicVolume / 100.0,
        val background: String = "panorama_1",
        val oobeCompleted: Boolean = false,
        val antiCheatEnabled: Boolean = true,
        val classicBackgroundColor: Int = 0xFF000000.toInt(),
        val classicBackgroundHue: Float = 0f,
        val classicBackgroundSaturation: Float = 0f,
        val classicBackgroundBrightness: Float = 0f,
        val classicBackgroundAlpha: Float = 1f,
        val classicBackgroundMode: String = "STATIC"
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

    private data class ColorChannels(
        val red: String,
        val green: String,
        val blue: String,
        val alpha: String?
    )

    private val edgeModuleAliases = mapOf(
        "oldanimations" to "animation",
        "performance" to "optimization",
        "clientsettings" to "clientsettings",
        "clickgui" to "clickgui",
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
        "itemcountdisplay" to "item_count"
    )

    private val legacyItemIdAliases = mapOf(
        262 to "minecraft:arrow",
        322 to "minecraft:golden_apple",
        368 to "minecraft:ender_pearl",
        373 to "minecraft:potion"
    )

    private val edgeValueAliases = mapOf(
        "optimization" to "ignorestands" to "ignorearmorstand",
        "optimization" to "entitiesoptimize" to "entityculling",
        "optimization" to "entitylimit" to "entitylimitation",
        "optimization" to "fpslimit" to "fpslosingfocus",
        "optimization" to "particleslimit" to "particlelimitation",
        "optimization" to "fontoptimize" to "fontoptimization",
        "optimization" to "limitchunks" to "chunkloadinglimitation",
        "optimization" to "chunkupdatelimit" to "chunkupdatinglimitation",
        "animation" to "oldblock" to "oldblocking",
        "animation" to "animationmode" to "animationmode",
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
