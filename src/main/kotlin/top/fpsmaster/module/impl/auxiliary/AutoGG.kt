package top.fpsmaster.module.impl.auxiliary

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import top.fpsmaster.command.CommandFeedback
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.module.value.impl.StringValue
import top.fpsmaster.text.ChatSender

class AutoGG : Module("auto-gg", Category.AUXILIARY) {
    init {
        values.addAll(
            arrayOf(
                autoPlay,
                delayToPlay,
                message,
                serverMode
            )
        )
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private val hypixelTriggers = arrayOf(
            "Reward Summary",
            "1st Killer",
            "Damage Dealt",
            "奖励总览",
            "击杀数第一名",
            "造成伤害"
        )
        private val normalTriggers = arrayOf(
            "获胜者",
            "第一名杀手",
            "击杀第一名"
        )

        private var active = false
        private var lastTriggerTime = 0L

        val autoPlay = OptionValue("auto-play", false)
        val delayToPlay = NumberValue("delay-to-play", 5.0, 0.0, 10.0, 1.0) { autoPlay.getValue() }
        val message = StringValue("message", "gg") { true }
        val serverMode = ChoiceValue("server-mode", listOf("hypixel", "normal"))

        @JvmStatic
        fun handleChat(component: Component) {
            if (!active) {
                return
            }

            val text = ChatFormatting.stripFormatting(component.string) ?: component.string
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime > 10_000L && matchesTrigger(text)) {
                val outgoing = if (serverMode.isSelected("hypixel")) {
                    "/ac ${message.getValue()}"
                } else {
                    message.getValue()
                }
                ChatSender.send(outgoing)
                lastTriggerTime = now

                if (serverMode.isSelected("normal") && autoPlay.getValue()) {
                    CommandFeedback.info("AutoPlay is not supported in normal mode yet")
                }
            }

            if (autoPlay.getValue() && serverMode.isSelected("hypixel")) {
                findPlayCommand(component)?.let { command ->
                    if (delayToPlay.getValue() > 0.0) {
                        CommandFeedback.info("Sending you to the next game in ${delayToPlay.getValue().toInt()} seconds")
                        Thread {
                            Thread.sleep((delayToPlay.getValue() * 1000L).toLong())
                            mc.execute { ChatSender.send(command) }
                        }.start()
                    } else {
                        ChatSender.send(command)
                    }
                }
            }
        }

        private fun matchesTrigger(text: String): Boolean {
            val triggers = if (serverMode.isSelected("hypixel")) hypixelTriggers else normalTriggers
            return triggers.any { text.contains(it) }
        }

        private fun findPlayCommand(component: Component): String? {
            val clickEvent = component.style.clickEvent
            //? if >=1.21.5 {
            if (clickEvent is ClickEvent.RunCommand && clickEvent.command().trim().lowercase().startsWith("/play ")) {
                return clickEvent.command()
            }
            //?} else {
            /*if (clickEvent != null && clickEvent.action == ClickEvent.Action.RUN_COMMAND && clickEvent.value.trim().lowercase().startsWith("/play ")) {
                return clickEvent.value
            }
            *///?}

            component.siblings.forEach { sibling ->
                findPlayCommand(sibling)?.let { return it }
            }
            return null
        }
    }
}
