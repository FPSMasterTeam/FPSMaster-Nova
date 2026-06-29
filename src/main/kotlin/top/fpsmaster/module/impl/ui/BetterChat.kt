package top.fpsmaster.module.impl.ui

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.OptionValue

class BetterChat : Module("better-chat", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                foldMessage,
                copyMessage
            )
        )
        style.addTo(this)
    }

    override fun onEnable() {
        active = true
        lastMessage = null
        counter = 1
    }

    override fun onDisable() {
        active = false
        lastMessage = null
        counter = 1
    }

    companion object {
        private var active = false
        private var lastMessage: String? = null
        private var counter = 1

        val foldMessage = OptionValue("fold-message", false)
        val copyMessage = OptionValue("copy-message", false)
        val style = HudStyle()

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun shouldFold(rawText: String): Boolean {
            if (!active || !foldMessage.getValue()) {
                lastMessage = rawText
                counter = 1
                return false
            }

            if (lastMessage == rawText) {
                counter++
                return true
            }

            lastMessage = rawText
            counter = 1
            return false
        }

        @JvmStatic
        fun decorate(component: Component, rawText: String, folded: Boolean): Component {
            if (!active) {
                return component
            }

            val copy = component.copy()
            if (folded) {
                copy.append(Component.literal(" [x$counter]").withStyle(ChatFormatting.WHITE))
            }
            if (copyMessage.getValue() && rawText.trim().length > 2) {
                copy.append(
                    Component.literal(" [C]").withStyle { style ->
                        style
                            .withColor(ChatFormatting.WHITE)
                            //? if >=1.21.5 {
                            .withClickEvent(ClickEvent.CopyToClipboard(rawText))
                            .withHoverEvent(HoverEvent.ShowText(Component.literal("Copy message")))
                            //?} else {
                            /*.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, rawText))
                            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Copy message")))*/
                            //?}
                    }
                )
            }
            return copy
        }

        @JvmStatic
        fun backgroundColor(vanillaAlpha: Float): Int {
            if (!active) {
                return alphaColor(0, 0, 0, (vanillaAlpha * 255.0f).toInt())
            }

            if (!style.background.getValue()) {
                return 0
            }

            val configured = style.backgroundColor.argb()
            val configuredAlpha = configured ushr 24
            val alpha = (configuredAlpha * vanillaAlpha).toInt().coerceIn(0, 255)
            return (alpha shl 24) or (configured and 0x00FFFFFF)
        }

        private fun alphaColor(red: Int, green: Int, blue: Int, alpha: Int): Int {
            return (alpha.coerceIn(0, 255) shl 24) or
                (red.coerceIn(0, 255) shl 16) or
                (green.coerceIn(0, 255) shl 8) or
                blue.coerceIn(0, 255)
        }
    }
}
