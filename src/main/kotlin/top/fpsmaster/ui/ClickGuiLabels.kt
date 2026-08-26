package top.fpsmaster.ui

import top.fpsmaster.module.Category
import top.fpsmaster.module.value.impl.ColorValue
import top.fpsmaster.translation.Language

/**
 * Translation-key resolution for the ClickGUI.
 *
 * Nova's language files were written with compacted keys (`blockoverlay.outlinecolor`), while module and
 * setting identities are kebab-case (`block-overlay` / `outline-color`). [compactKey] bridges the two so
 * new settings do not need duplicate entries, and every lookup falls back to the raw identity instead of
 * rendering a missing-key placeholder.
 */
internal fun compactKey(id: String): String = id.filter { it.isLetterOrDigit() }

internal fun moduleLabel(identity: String): String {
    val compact = compactKey(identity)
    val translated = Language.get(compact)
    return if (translated != compact) translated else Language.get(identity)
}

internal fun settingLabel(moduleId: String, settingId: String): String {
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

/** Choice options prefer a module-scoped key, then a shared `choice.<option>`, then the raw option. */
internal fun choiceLabel(moduleId: String, settingId: String, option: String): String {
    val scoped = "${compactKey(moduleId)}.${compactKey(settingId)}.${compactKey(option)}"
    val fromScoped = Language.get(scoped)
    if (fromScoped != scoped) {
        return fromScoped
    }
    val generic = "choice.${compactKey(option)}"
    val fromGeneric = Language.get(generic)
    return if (fromGeneric != generic) fromGeneric else option
}

internal fun colorModeLabel(mode: ColorValue.Mode): String {
    val key = "colormode.${compactKey(mode.id)}"
    val translated = Language.get(key)
    return if (translated != key) translated else mode.id
}

internal fun Category.toId(): String = when (this) {
    Category.OPTIMIZATION -> "optimize"
    Category.RENDER -> "render"
    Category.AUXILIARY -> "utility"
    Category.UI -> "interface"
}
