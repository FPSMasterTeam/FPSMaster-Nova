package top.fpsmaster.command.impl

import net.minecraft.network.chat.Component
import top.fpsmaster.command.Command
import top.fpsmaster.mc
import top.fpsmaster.module.ModuleManager

class Toggle : Command("toggle") {
    override fun execute(args: Array<String>) {
        if (args.size != 1) {
            mc.gui.chat.addMessage(Component.literal("\u00a7cBro toggle a module"))
        } else {
            ModuleManager.modules[args[0].lowercase()]?.let {
                it.enabled = !it.enabled
                mc.gui.chat.addMessage(Component.literal("\u00a7aToggled " + it.identity))
            } ?: run {
                mc.gui.chat.addMessage(Component.literal("\u00a7cBro module not found"))
            }
        }
    }
}