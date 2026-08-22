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
import top.fpsmaster.prism.screen.ClickGuiBridge
import top.fpsmaster.prism.screen.SharedClickGui
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.hud.HudEditorScreen
import java.awt.Color

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
                val settings = mutableListOf<ClickGuiBridge.SettingInfo>()
                val consumed = mutableSetOf<String>()
                module.values.forEach { value ->
                    if (!consumed.add(value.getIdentity())) return@forEach
                    val prefix = value.getIdentity().removeSuffix("-red")
                    val channels = if (prefix != value.getIdentity()) listOf("red", "green", "blue", "alpha").map { channel ->
                        module.values.find { it.getIdentity() == "$prefix-$channel" } as? NumberValue
                    } else emptyList()
                    if (channels.size == 4 && channels.all { it != null }) {
                        val rgba = channels.filterNotNull()
                        consumed.addAll(rgba.map { it.getIdentity() })
                        val hsb = Color.RGBtoHSB(
                            rgba[0].getValue().toInt().coerceIn(0, 255),
                            rgba[1].getValue().toInt().coerceIn(0, 255),
                            rgba[2].getValue().toInt().coerceIn(0, 255),
                            null
                        )
                        val group = value.group?.let { ClickGuiBridge.GroupInfo(it.id, "", it.collapsedByDefault) }
                        settings += ClickGuiBridge.SettingInfo(
                            "$prefix-color",
                            settingLabel(module.identity, "$prefix-color"),
                            hsb[0], hsb[1], hsb[2],
                            (rgba[3].getValue() / 255.0).toFloat(),
                            "", emptyList()
                        ).presentation(rgba.all { it.isDisplayable() }, group)
                        return@forEach
                    }
                    val label = settingLabel(module.identity, value.getIdentity())
                    val info = when (value) {
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
                    val group = value.group?.let {
                        ClickGuiBridge.GroupInfo(it.id, "", it.collapsedByDefault)
                    }
                    settings += info.presentation(value.isDisplayable(), group)
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

        override fun setColor(
            moduleId: String,
            settingId: String,
            hue: Float,
            saturation: Float,
            brightness: Float,
            alpha: Float,
            mode: String
        ) {
            val prefix = settingId.removeSuffix("-color")
            if (prefix == settingId) return
            val rgb = Color.HSBtoRGB(hue, saturation, brightness)
            val channels = intArrayOf((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255, (alpha * 255f).toInt())
            listOf("red", "green", "blue", "alpha").forEachIndexed { index, channel ->
                val number = ModuleManager.modules[moduleId]?.values
                    ?.find { it.getIdentity() == "$prefix-$channel" } as? NumberValue
                number?.setValue(channels[index].toDouble())
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

        override fun openHudEditor() {
            mc.setScreenCompat(HudEditorScreen())
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
