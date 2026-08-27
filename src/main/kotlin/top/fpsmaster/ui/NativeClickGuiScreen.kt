package top.fpsmaster.ui

import net.minecraft.network.chat.Component
import top.fpsmaster.Client
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.setScreenCompat
import top.fpsmaster.config.ProfileAutoSave
import top.fpsmaster.module.Module
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.module.value.Value
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.ColorValue
import top.fpsmaster.module.value.impl.KeyValue
import top.fpsmaster.module.value.impl.ListValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.ToolkitScreen
import top.fpsmaster.prism.screen.ClickGuiBridge
import top.fpsmaster.prism.screen.SharedClickGui
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.hud.HudEditorScreen

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

    override fun removed() {
        ProfileAutoSave.flush()
        super.removed()
    }

    private class NovaClickGuiBridge(private val host: NativeClickGuiScreen) : ClickGuiBridge {
        override fun i18n(key: String): String = Language.get(key)
        override fun settingGroupLabel(groupId: String): String = Language.get("group.$groupId")
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
                ClickGuiBridge.ModInfo(
                    module.identity,
                    moduleLabel(module.identity),
                    module.enabled,
                    module.canBeEnabled,
                    module.values.distinctBy { it.getIdentity() }.map { value -> settingInfo(module, value) },
                    module.key,
                    KeyValue.nameOf(module.key)
                )
            }
        }

        private fun settingInfo(module: Module, value: Value<*>): ClickGuiBridge.SettingInfo {
            val id = value.getIdentity()
            val label = settingLabel(module.identity, id)
            val info = when (value) {
                is OptionValue -> ClickGuiBridge.SettingInfo(id, label, value.getValue())
                is NumberValue -> ClickGuiBridge.SettingInfo(
                    id,
                    label,
                    value.getValue(),
                    value.minimum,
                    value.maximum
                )
                is StringValue -> ClickGuiBridge.SettingInfo(id, label, value.getValue())
                is ChoiceValue -> ClickGuiBridge.SettingInfo(
                    id,
                    label,
                    value.options.map { choiceLabel(module.identity, id, it) },
                    value.index
                )
                is ColorValue -> {
                    val snapshot = value.getValue()
                    ClickGuiBridge.SettingInfo(
                        id,
                        label,
                        snapshot.hue,
                        snapshot.saturation,
                        snapshot.brightness,
                        snapshot.alpha,
                        colorModeLabel(snapshot.mode),
                        if (value.modes.size > 1) value.modes.map(::colorModeLabel) else emptyList()
                    )
                }
                is KeyValue -> ClickGuiBridge.SettingInfo(id, label, value.getValue(), value.keyName())
                is ListValue -> ClickGuiBridge.SettingInfo(
                    id,
                    label,
                    value.getValue().map { entry ->
                        ClickGuiBridge.ListItem(entry.text, entry.keyCode, KeyValue.nameOf(entry.keyCode))
                    },
                    value.capacity,
                    value.keyed
                )
                else -> ClickGuiBridge.SettingInfo(id, label, value.getValue().toString())
            }
            val group = value.group?.let {
                ClickGuiBridge.GroupInfo(it.id, Language.get("group.${it.id}"), it.collapsedByDefault)
            }
            return info.presentation(value.isDisplayable(), group)
        }

        override fun toggle(moduleId: String) {
            val module = ModuleManager.modules[moduleId] ?: return
            if (module.canBeEnabled) {
                module.enabled = !module.enabled
                ProfileAutoSave.save()
            }
        }

        override fun setModuleKey(moduleId: String, keyCode: Int) {
            val module = ModuleManager.modules[moduleId] ?: return
            module.key = keyCode
            ProfileAutoSave.save()
        }

        override fun setNumber(moduleId: String, settingId: String, value: Double) {
            val number = setting(moduleId, settingId) as? NumberValue ?: return
            number.setValue(value)
            ProfileAutoSave.coalesce()
        }

        override fun setBool(moduleId: String, settingId: String, value: Boolean) {
            val option = setting(moduleId, settingId) as? OptionValue ?: return
            option.setValue(value)
            ProfileAutoSave.save()
        }

        override fun setText(moduleId: String, settingId: String, value: String) {
            val text = setting(moduleId, settingId) as? StringValue ?: return
            try {
                text.setValue(value)
            } catch (_: IllegalArgumentException) {
                return
            }
            ProfileAutoSave.coalesce()
        }

        override fun setChoice(moduleId: String, settingId: String, index: Int) {
            val choice = setting(moduleId, settingId) as? ChoiceValue ?: return
            choice.select(index)
            ProfileAutoSave.save()
        }

        override fun setColor(
            moduleId: String,
            settingId: String,
            hue: Float,
            saturation: Float,
            brightness: Float,
            alpha: Float,
            mode: String
        ) {
            val color = setting(moduleId, settingId) as? ColorValue ?: return
            val resolved = color.modes.firstOrNull { colorModeLabel(it) == mode }
                ?: ColorValue.Mode.of(mode)
                ?: color.getValue().mode
            color.set(hue, saturation, brightness, alpha, resolved)
            ProfileAutoSave.coalesce()
        }

        override fun setKey(moduleId: String, settingId: String, keyCode: Int) {
            val key = setting(moduleId, settingId) as? KeyValue ?: return
            key.setValue(keyCode)
            ProfileAutoSave.save()
        }

        override fun addListItem(moduleId: String, settingId: String) {
            val list = setting(moduleId, settingId) as? ListValue ?: return
            if (list.add()) {
                ProfileAutoSave.save()
            }
        }

        override fun removeListItem(moduleId: String, settingId: String, index: Int) {
            val list = setting(moduleId, settingId) as? ListValue ?: return
            if (list.removeAt(index)) {
                ProfileAutoSave.save()
            }
        }

        override fun setListItemText(moduleId: String, settingId: String, index: Int, value: String) {
            val list = setting(moduleId, settingId) as? ListValue ?: return
            if (list.setText(index, value)) {
                ProfileAutoSave.coalesce()
            }
        }

        override fun setListItemKey(moduleId: String, settingId: String, index: Int, keyCode: Int) {
            val list = setting(moduleId, settingId) as? ListValue ?: return
            if (list.setKey(index, keyCode)) {
                ProfileAutoSave.save()
            }
        }

        override fun lightTheme(): Boolean = ClientSettings.lightTheme()

        override fun toggleTheme() {
            ClientSettings.theme.setValue(if (lightTheme()) "dark" else "light")
            ProfileAutoSave.save()
        }

        override fun openMusic() {
            mc.setScreenCompat(NativeMusicScreen(host))
        }

        override fun openProfiles() {
            mc.setScreenCompat(NativeConfigProfilesScreen(host))
        }

        override fun openCosmetics() {
            mc.setScreenCompat(NativeCosmeticsScreen(host))
        }

        override fun openHudEditor() {
            mc.setScreenCompat(HudEditorScreen())
        }

        private fun setting(moduleId: String, settingId: String): Value<*>? =
            ModuleManager.modules[moduleId]?.values?.find { it.getIdentity() == settingId }

        private fun modulesOf(categoryId: String) =
            ModuleManager.modules.values.filter { it.category.toId() == categoryId }
    }
}
