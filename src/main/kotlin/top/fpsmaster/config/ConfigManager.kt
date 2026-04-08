package top.fpsmaster.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.value.Value
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.web.network.packets.PacketRegistryInitializer
import top.fpsmaster.mc
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.reader
import kotlin.io.path.writer

object ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val validNameRegex = Regex("^[a-zA-Z0-9._-]+$")

    fun create(name: String) {
        val path = configPath(name)
        if (path.exists()) {
            throw CommandExecutionException("配置已存在: $name")
        }

        saveSnapshot(path)
    }

    fun load(name: String) {
        val path = configPath(name)
        if (!path.exists() || !path.isRegularFile()) {
            throw CommandExecutionException("配置不存在: $name")
        }

        val config = path.reader().use { gson.fromJson(it, ConfigFile::class.java) }
            ?: throw CommandExecutionException("配置文件无效: $name")

        config.modules.forEach { moduleEntry ->
            val module = ModuleManager.modules[moduleEntry.id.lowercase()] ?: return@forEach
            module.key = moduleEntry.key
            moduleEntry.values.entrySet().forEach { valueEntry ->
                val value = module.values.firstOrNull { it.getIdentity().equals(valueEntry.key, ignoreCase = true) }
                    ?: return@forEach
                applyValue(value, valueEntry.value)
            }
            module.enabled = moduleEntry.enabled
        }

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
        val config = ConfigFile(
            modules = ModuleManager.modules.values.map { module ->
                ConfigModule(
                    id = module.identity,
                    enabled = module.enabled,
                    key = module.key,
                    values = buildJsonObject {
                        module.values.forEach { value ->
                            add(value.getIdentity(), toJson(value))
                        }
                    }
                )
            }
        )

        path.writer().use { writer ->
            gson.toJson(config, writer)
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

    private fun toJson(value: Value<*>): JsonElement {
        return when (value) {
            is OptionValue -> gson.toJsonTree(value.getValue())
            is NumberValue -> gson.toJsonTree(value.getValue())
            is StringValue -> gson.toJsonTree(value.getValue())
            else -> throw CommandExecutionException("不支持的值类型: ${value::class.simpleName}")
        }
    }

    private fun configPath(name: String): Path {
        if (!validNameRegex.matches(name)) {
            throw CommandExecutionException("配置名只能包含字母、数字、点、下划线和短横线")
        }

        return ensureConfigDirectory().resolve("$name.json")
    }

    private fun ensureConfigDirectory(): Path {
        val directory = mc.gameDirectory.toPath()
            .resolve("fpsmaster")
            .resolve("configs")
        Files.createDirectories(directory)
        return directory
    }

    private inline fun buildJsonObject(builder: JsonObject.() -> Unit): JsonObject {
        return JsonObject().apply(builder)
    }

    private data class ConfigFile(
        val modules: List<ConfigModule> = emptyList()
    )

    private data class ConfigModule(
        val id: String = "",
        val enabled: Boolean = false,
        val key: Int = 0,
        val values: JsonObject = JsonObject()
    )
}
