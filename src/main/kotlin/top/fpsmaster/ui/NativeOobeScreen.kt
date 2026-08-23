package top.fpsmaster.ui

import net.minecraft.network.chat.Component
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.mc
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen
import top.fpsmaster.prism.theme.Metrics
import top.fpsmaster.prism.widget.Chrome
import top.fpsmaster.prism.widget.UiFrame

open class NativeOobeScreen : ToolkitScreen(Component.literal("FPSMaster OOBE")) {
    override fun renderUi(ui: UiFrame) {
        Chrome.veil(ui, 1f)
        val w = 280f
        val h = 140f
        val x = (width - w) / 2f
        val y = (height - h) / 2f
        Chrome.panel(ui, x, y, w, h)
        ui.canvas().drawString(ui.font(18), "FPSMaster", x + 16f, y + 16f, ui.theme().textPrimary())
        ui.canvas().drawString(ui.font(12), Language.get("oobe.welcome.title") , x + 16f, y + 40f, ui.theme().textSecondary())

        val zh = ClientSettings.language.getValue().toInt() == 1
        ui.canvas().drawString(ui.font(12), Language.get("oobe.language.title"), x + 16f, y + 64f, ui.theme().textPrimary())
        if (Chrome.button(ui, x + 90f, y + 60f, 50f, Metrics.BTN_H, "EN", if (!zh) Chrome.ButtonStyle.PRIMARY else Chrome.ButtonStyle.DEFAULT)) {
            ClientSettings.language.setValue(0.0)
        }
        if (Chrome.button(ui, x + 146f, y + 60f, 50f, Metrics.BTN_H, "中文", if (zh) Chrome.ButtonStyle.PRIMARY else Chrome.ButtonStyle.DEFAULT)) {
            ClientSettings.language.setValue(1.0)
        }

        if (Chrome.button(ui, x + 16f, y + h - 28f, w - 32f, Metrics.BTN_H, Language.get("oobe.done.start"), Chrome.ButtonStyle.PRIMARY)) {
            ConfigManager.completeOobe()
            top.fpsmaster.Client.openMainMenu()
        }
    }

    override fun shouldCloseOnEsc(): Boolean = false
}
