package top.fpsmaster.text

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import top.fpsmaster.module.impl.auxiliary.NameProtect

object GlobalTextFilter {
    @JvmStatic
    fun filter(text: String?): String {
        return NameProtect.filter(text)
    }

    @JvmStatic
    fun filter(component: Component?): Component? {
        if (component == null) {
            return null
        }

        val filtered = filter(component.string)
        if (filtered == component.string) {
            return component
        }

        return Component.literal(filtered).withStyle(component.style)
    }

    @JvmStatic
    fun filter(text: FormattedText?): FormattedText? {
        if (text == null) {
            return null
        }

        val plainText = text.string
        val filtered = filter(plainText)
        if (filtered == plainText) {
            return text
        }

        return Component.literal(filtered)
    }
}
