package top.fpsmaster.module.impl.ui

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.EntityHitResult
import java.util.Locale
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module

class ReachDisplay : Module("reach-display", Category.UI) {
    init {
        textColor.addTo(this)
        style.addTo(this)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        reach = 0.0
    }

    companion object {
        private var active = false
        private var reach = 0.0
        val textColor = HudTextColor()
        val style = HudStyle()

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun textColorValue(): Int = textColor.argb()

        @JvmStatic
        fun recordAttack(minecraft: Minecraft) {
            if (!active) {
                return
            }

            val player = minecraft.player ?: return
            val hitResult = minecraft.hitResult as? EntityHitResult ?: return
            reach = player.eyePosition.distanceTo(hitResult.location)
        }

        @JvmStatic
        fun text(): String {
            return String.format(Locale.US, "%.2f b", reach)
        }
    }
}
