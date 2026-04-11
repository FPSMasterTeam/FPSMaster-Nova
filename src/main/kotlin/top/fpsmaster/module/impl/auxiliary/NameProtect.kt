package top.fpsmaster.module.impl.auxiliary

import net.minecraft.client.Minecraft
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.StringValue

class NameProtect : Module("name-protect", Category.AUXILIARY) {
    init {
        values.addAll(
            arrayOf(
                name,
                replacement
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
        @JvmField
        val name = StringValue("name", "")

        @JvmField
        val replacement = StringValue("replacement", "Hide")

        private var active = false

        @JvmStatic
        fun filter(raw: String?): String {
            if (!active || raw.isNullOrEmpty()) {
                return raw ?: ""
            }

            val target = name.getValue().ifBlank {
                Minecraft.getInstance().player?.scoreboardName.orEmpty()
            }

            if (target.isEmpty()) {
                return raw
            }

            return raw.replace(target, replacement.getValue())
        }
    }
}
