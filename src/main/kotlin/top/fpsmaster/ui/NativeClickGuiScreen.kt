package top.fpsmaster.ui

import net.minecraft.network.chat.Component
import top.fpsmaster.Client
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.setScreenCompat
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen
import top.fpsmaster.uikit.screen.ClickGuiBridge
import top.fpsmaster.uikit.screen.SharedClickGui
import top.fpsmaster.uikit.widget.UiFrame

class NativeClickGuiScreen : ToolkitScreen(Component.literal("FPSMaster")) {
    private val gui = SharedClickGui("optimize")
    private val bridge = NovaClickGuiBridge(this)

    override fun init() {
        super.init()
        gui.onOpen()
    }

    override fun renderUi(ui: UiFrame) {
        if (gui.draw(ui, bridge)) {
            mc.setScreenCompat(null)
        }
    }

    override fun handleEscape(): Boolean {
        gui.beginClose()
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    private class NovaClickGuiBridge(private val host: NativeClickGuiScreen) : ClickGuiBridge {
        override fun i18n(key: String): String = Language.get(key)
        override fun edition(): String = "NOVA"
        override fun version(): String = Client.VERSION

        override fun categories(): List<String> = Category.entries.map { it.toId() }

        override fun categoryLabel(id: String): String = Language.get("category.$id")

        override fun categoryIcon(id: String): String = when (id) {
            "optimize" -> "zap"
            "render" -> "sparkles"
            "utility" -> "wrench"
            "interface" -> "grid"
            else -> "box"
        }

        override fun moduleCount(categoryId: String): Int = modulesOf(categoryId).size

        override fun enabledCount(categoryId: String): Int = modulesOf(categoryId).count { it.enabled }

        override fun modules(categoryId: String, query: String): List<ClickGuiBridge.ModInfo> {
            val q = query.trim()
            val source = if (q.isEmpty()) {
                modulesOf(categoryId)
            } else {
                ModuleManager.modules.values.filter { module ->
                    Language.get(module.identity).contains(q, ignoreCase = true) ||
                        module.identity.contains(q, ignoreCase = true)
                }
            }
            return source.map { module ->
                val settings = module.values.filter { it.isDisplayable() }.map { value ->
                    val label = settingLabel(module.identity, value.getIdentity())
                    when (value) {
                        is OptionValue -> ClickGuiBridge.SettingInfo(value.getIdentity(), label, value.getValue())
                        is NumberValue -> ClickGuiBridge.SettingInfo(
                            value.getIdentity(),
                            label,
                            value.getValue(),
                            value.minimum,
                            value.maximum
                        )
                        is StringValue -> ClickGuiBridge.SettingInfo(
                            value.getIdentity(),
                            label,
                            value.getValue()
                        )
                        else -> ClickGuiBridge.SettingInfo(value.getIdentity(), label, value.getValue().toString())
                    }
                }
                ClickGuiBridge.ModInfo(
                    module.identity,
                    moduleLabel(module.identity),
                    module.enabled,
                    module.canBeEnabled && !module.unsupported,
                    settings
                )
            }
        }

        override fun toggle(moduleId: String) {
            val module = ModuleManager.modules[moduleId] ?: return
            if (module.canBeEnabled && !module.unsupported) {
                module.enabled = !module.enabled
            }
        }

        override fun setNumber(moduleId: String, settingId: String, value: Double) {
            val number = ModuleManager.modules[moduleId]?.values?.find { it.getIdentity() == settingId } as? NumberValue
            number?.setValue(value)
        }

        override fun setBool(moduleId: String, settingId: String, value: Boolean) {
            val option = ModuleManager.modules[moduleId]?.values?.find { it.getIdentity() == settingId } as? OptionValue
            option?.setValue(value)
        }

        override fun setText(moduleId: String, settingId: String, value: String) {
            val text = ModuleManager.modules[moduleId]?.values?.find { it.getIdentity() == settingId } as? StringValue
            try {
                text?.setValue(value)
            } catch (_: IllegalArgumentException) {
            }
        }

        override fun lightTheme(): Boolean = ClientSettings.theme.getValue().toInt() == 1

        override fun toggleTheme() {
            ClientSettings.theme.setValue(if (lightTheme()) 0.0 else 1.0)
        }

        override fun openMusic() {
            mc.setScreenCompat(NativeMusicScreen(host))
        }

        override fun openProfiles() {
            mc.setScreenCompat(NativeConfigProfilesScreen(host))
        }

        override fun webUi(): Boolean = top.fpsmaster.config.ConfigManager.webUi()

        override fun toggleWebUi() {
            top.fpsmaster.Client.setWebUi(!webUi())
        }

        private fun modulesOf(categoryId: String) =
            ModuleManager.modules.values.filter { it.category.toId() == categoryId }
    }
}

private fun compactKey(id: String): String = id.filter { it.isLetterOrDigit() }

private fun moduleLabel(identity: String): String {
    val compact = compactKey(identity)
    val translated = Language.get(compact)
    return if (translated != compact) translated else Language.get(identity)
}

private fun settingLabel(moduleId: String, settingId: String): String {
    val compactMod = compactKey(moduleId)
    val compactSet = compactKey(settingId)
    val dotted = "$compactMod.$compactSet"
    val fromDotted = Language.get(dotted)
    if (fromDotted != dotted) {
        return fromDotted
    }
    val fromSet = Language.get(compactSet)
    if (fromSet != compactSet) {
        return fromSet
    }
    return Language.get(settingId)
}

private fun Category.toId(): String = when (this) {
    Category.OPTIMIZATION -> "optimize"
    Category.RENDER -> "render"
    Category.AUXILIARY -> "utility"
    Category.UI -> "interface"
}
