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
import top.fpsmaster.prism.theme.Metrics
import top.fpsmaster.prism.widget.Chrome
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.logger
import top.fpsmaster.notification.NotificationManager
import top.fpsmaster.prism.overlay.NotificationCenter
import top.fpsmaster.ui.kit.NativeDialogs

class NativeConfigProfilesScreen(
    private val parent: net.minecraft.client.gui.screens.Screen?
) : ToolkitScreen(Component.literal("Config")) {
    private val gui = SharedConfigProfiles()
    private val bridge = NovaProfilesBridge()

    /** Until when the rollback button is armed: it needs a second click within 3 s. */
    private var rollbackArmedUntil = 0L

    override fun renderUi(ui: UiFrame) {
        if (gui.draw(ui, bridge)) {
            mc.setScreenCompat(parent)
            return
        }
        drawRollback(ui)
    }

    /**
     * "Roll back to the last backup", under the profiles panel. Prism's profile screen has no slot
     * for it, so it sits just below the panel, whose size mirrors SharedConfigProfiles.draw.
     */
    private fun drawRollback(ui: UiFrame) {
        val gw = ui.host().width()
        val gh = ui.host().height()
        val ph = minOf(250f, maxOf(180f, gh - 32f))
        val panelBottom = (gh - ph) / 2f + ph
        val h = Metrics.BTN_H
        val y = panelBottom + 4f
        if (y + h > gh) {
            return
        }
        val armed = System.currentTimeMillis() < rollbackArmedUntil
        val label = Language.get(if (armed) "configbackup.rollback.confirm" else "configbackup.rollback")
        val w = ui.font(11).measure(label) + 16f
        if (!Chrome.button(ui, (gw - w) / 2f, y, w, h, label,
                if (armed) Chrome.ButtonStyle.DANGER else Chrome.ButtonStyle.GHOST)) {
            return
        }
        if (!armed) {
            rollbackArmedUntil = System.currentTimeMillis() + 3000L
            return
        }
        rollbackArmedUntil = 0L
        val (message, ok) = try {
            Language.get("configbackup.rollback.done").format(ConfigManager.rollbackActive()) to true
        } catch (failure: CommandExecutionException) {
            failure.message.orEmpty() to false
        } catch (failure: Exception) {
            logger.warn("Config rollback failed", failure)
            Language.get("configbackup.rollback.failed") to false
        }
        NotificationManager.add(
            Language.get("configbackup.title"),
            message,
            3f,
            if (ok) NotificationCenter.Type.SUCCESS else NotificationCenter.Type.ERROR
        )
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

        override fun load(name: String): ConfigProfilesBridge.Result = run("configprofiles.status.loaded", "configprofiles.status.load_failed") {
            ConfigManager.loadProfile(name)
            name
        }

        override fun delete(name: String): ConfigProfilesBridge.Result = run("configprofiles.status.deleted", "configprofiles.status.delete_failed") {
            ConfigManager.delete(name)
            name
        }

        override fun rename(from: String, to: String): ConfigProfilesBridge.Result =
            run("configprofiles.status.renamed", "configprofiles.status.rename_failed") {
                ConfigManager.rename(from, to)
            }

        override fun create(name: String): ConfigProfilesBridge.Result = run("configprofiles.status.ready", "configprofiles.status.load_failed") {
            ConfigManager.create(name)
            name
        }

        override fun exportActive(): ConfigProfilesBridge.Result = run("configprofiles.status.exported", "configprofiles.status.export_failed") {
            ConfigManager.exportProfile(ConfigManager.activeName())
        }

        // The picker runs off the render thread (see NativeDialogs), so the import outcome arrives
        // after this call returned; it is reported as a notification over the screen instead.
        override fun importFile(): ConfigProfilesBridge.Result {
            val started = NativeDialogs.pickFiles(
                Language.get("configprofiles.filedialog.import"),
                listOf("*.json"),
                "JSON",
                multiple = false
            ) { selected ->
                val file = selected.firstOrNull() ?: return@pickFiles
                val result = run("configprofiles.status.imported", "configprofiles.status.import_failed") {
                    ConfigManager.importProfile(file.toString())
                }
                NotificationManager.add(
                    Language.get("configprofiles.importfile"),
                    result.message,
                    3f,
                    if (result.ok) NotificationCenter.Type.SUCCESS else NotificationCenter.Type.ERROR
                )
            }
            return if (started) ConfigProfilesBridge.Result.ok(Language.get("configprofiles.status.picking"))
            else ConfigProfilesBridge.Result.failed(Language.get("configprofiles.status.picker_busy"))
        }

        override fun resetAllOff(): ConfigProfilesBridge.Result = run("configprofiles.status.alloff", "configprofiles.status.alloff_failed") {
            ConfigManager.resetActiveToAllOff()
            ConfigManager.activeName()
        }

        private fun run(okKey: String, failKey: String, block: () -> String): ConfigProfilesBridge.Result {
            return try {
                val arg = block()
                val message = try {
                    String.format(Language.get(okKey), arg)
                } catch (_: Exception) {
                    Language.get(okKey)
                }
                ConfigProfilesBridge.Result.ok(message)
            } catch (failure: CommandExecutionException) {
                ConfigProfilesBridge.Result.failed(Language.get(failKey) + ": " + failure.message)
            } catch (err: Exception) {
                logger.warn("Config profile operation failed", err)
                ConfigProfilesBridge.Result.failed(Language.get(failKey))
            }
        }
    }
}
