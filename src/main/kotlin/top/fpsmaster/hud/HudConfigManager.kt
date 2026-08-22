package top.fpsmaster.hud

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader
import kotlin.io.path.writer
import top.fpsmaster.mc

object HudConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        val path = configPath()
        if (!path.exists() || !path.isRegularFile()) {
            save()
            return
        }

        val config = path.reader().use { gson.fromJson(it, HudConfigFile::class.java) } ?: return
        config.components.forEach { entry ->
            val component = HudManager.components[entry.id] ?: return@forEach
            component.x = entry.x
            component.y = entry.y
            component.scale = entry.scale
            component.visible = entry.visible
            component.relativeX = entry.relativeX ?: Float.NaN
            component.relativeY = entry.relativeY ?: Float.NaN
        }
    }

    fun save() {
        val path = configPath()
        val config = HudConfigFile(
            components = HudManager.components.values.map { component ->
                HudComponentEntry(
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

        path.writer().use { writer ->
            gson.toJson(config, writer)
        }
    }

    private fun configPath(): Path {
        val directory = mc.gameDirectory.toPath()
            .resolve("fpsmaster")
        Files.createDirectories(directory)
        return directory.resolve("hud.json")
    }

    private data class HudConfigFile(
        val components: List<HudComponentEntry> = emptyList()
    )

    private data class HudComponentEntry(
        val id: String = "",
        val x: Float = 0f,
        val y: Float = 0f,
        val scale: Float = 1f,
        val visible: Boolean = true,
        val relativeX: Float? = null,
        val relativeY: Float? = null
    )
}
