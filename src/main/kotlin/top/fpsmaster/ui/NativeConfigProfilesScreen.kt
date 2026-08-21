package top.fpsmaster.ui

import net.minecraft.network.chat.Component
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen
import top.fpsmaster.prism.screen.ConfigProfilesBridge
import top.fpsmaster.prism.screen.SharedConfigProfiles
import top.fpsmaster.prism.widget.UiFrame
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Paths

class NativeConfigProfilesScreen(
    private val parent: net.minecraft.client.gui.screens.Screen?
) : ToolkitScreen(Component.literal("Config")) {
    private val gui = SharedConfigProfiles()
    private val bridge = NovaProfilesBridge()

    override fun renderUi(ui: UiFrame) {
        if (gui.draw(ui, bridge)) {
            mc.setScreenCompat(parent)
        }
    }

    override fun handleEscape(): Boolean {
        mc.setScreenCompat(parent)
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    private class NovaProfilesBridge : ConfigProfilesBridge {
        override fun i18n(key: String): String = Language.get(key)
        override fun activeName(): String = ConfigManager.activeName()
        override fun profiles(): List<ConfigProfilesBridge.Profile> =
            ConfigManager.listProfiles().map { ConfigProfilesBridge.Profile(it.name, it.lastModified, it.bytes) }

        override fun enabledModules(): Int = ModuleManager.modules.values.count { it.enabled }
        override fun hudModules(): Int = ModuleManager.modules.values.count { it.category == Category.UI && it.enabled }

        override fun activeBytes(): Long =
            ConfigManager.listProfiles().firstOrNull { it.name == ConfigManager.activeName() }?.bytes ?: 0L

        override fun activeModified(): Long =
            ConfigManager.listProfiles().firstOrNull { it.name == ConfigManager.activeName() }?.lastModified ?: 0L

        override fun isDefault(name: String): Boolean = ConfigManager.isDefaultProfile(name)

        override fun load(name: String): String = run("configprofiles.status.loaded", "configprofiles.status.load_failed") {
            ConfigManager.loadProfile(name)
            name
        }

        override fun delete(name: String): String = run("configprofiles.status.deleted", "configprofiles.status.delete_failed") {
            ConfigManager.delete(name)
            name
        }

        override fun rename(from: String, to: String): String =
            run("configprofiles.status.renamed", "configprofiles.status.rename_failed") {
                ConfigManager.rename(from, to)
            }

        override fun create(name: String): String = run("configprofiles.status.ready", "configprofiles.status.load_failed") {
            ConfigManager.create(name)
            name
        }

        override fun exportActive(): String = run("configprofiles.status.exported", "configprofiles.status.export_failed") {
            ConfigManager.exportProfile(ConfigManager.activeName())
        }

        override fun importFile(): String = run("configprofiles.status.imported", "configprofiles.status.import_failed") {
            val dialog = FileDialog(null as Frame?, Language.get("configprofiles.filedialog.import"), FileDialog.LOAD)
            dialog.isVisible = true
            val file = dialog.file ?: throw CommandExecutionException("cancelled")
            val dir = dialog.directory ?: ""
            ConfigManager.importProfile(Paths.get(dir, file).toString())
        }

        override fun resetAllOff(): String = run("configprofiles.status.alloff", "configprofiles.status.alloff_failed") {
            ConfigManager.resetActiveToAllOff()
            ConfigManager.activeName()
        }

        private fun run(okKey: String, failKey: String, block: () -> String): String {
            return try {
                val arg = block()
                try {
                    String.format(Language.get(okKey), arg)
                } catch (_: Exception) {
                    Language.get(okKey)
                }
            } catch (cancelled: CommandExecutionException) {
                if (cancelled.message == "cancelled") Language.get("configprofiles.status.ready")
                else Language.get(failKey) + ": " + cancelled.message
            } catch (err: Exception) {
                Language.get(failKey)
            }
        }
    }
}
