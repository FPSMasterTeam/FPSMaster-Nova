package top.fpsmaster.module.impl.ui

import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import kotlin.math.sin

class TargetDisplay : Module("target-display", Category.UI) {
    init {
        values.addAll(
            arrayOf(
                targetEsp,
                targetHud,
                omitName
            )
        )
        espColor.addTo(this)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        target = null
    }

    companion object {
        private var active = false
        val targetEsp = NumberValue("target-esp", 0.0, 0.0, 1.0, 1.0)
        val espColor = HudTextColor("esp", 255.0, 255.0, 255.0, 255.0)
        val targetHud = NumberValue("target-hud", 0.0, 0.0, 2.0, 1.0)
        val omitName = OptionValue("omit-name", true)
        var target: Player? = null
            private set
        private var lastHit = 0L

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun hudMode(): Int = targetHud.getValue().toInt()

        @JvmStatic
        fun shouldOmitName(): Boolean = omitName.getValue()

        @JvmStatic
        fun recordAttack(minecraft: Minecraft) {
            if (!active) {
                return
            }

            target = (minecraft.hitResult as? EntityHitResult)?.entity as? Player
            if (target != null) {
                lastHit = System.currentTimeMillis()
            }
        }

        @JvmStatic
        fun activeTarget(): Player? {
            return activeTarget(3_000L, clearWhenExpired = true)
        }

        @JvmStatic
        fun activeHudTarget(): Player? {
            return activeTarget(5_000L, clearWhenExpired = true)
        }

        private fun activeTarget(maxAgeMillis: Long, clearWhenExpired: Boolean): Player? {
            val value = target ?: return null
            if (!value.isAlive || System.currentTimeMillis() - lastHit > maxAgeMillis) {
                if (clearWhenExpired) {
                    target = null
                }
                return null
            }
            return value
        }

        @JvmStatic
        fun emitTargetEsp(partialTick: Float) {
            if (!active || targetEsp.getValue().toInt() != 0) {
                return
            }

            val value = activeTarget(3_000L, clearWhenExpired = false) ?: return
            val x = value.xOld + (value.x - value.xOld) * partialTick
            val y = value.yOld + (value.y - value.yOld) * partialTick + sin(System.currentTimeMillis() / 200.0) + 1.0
            val z = value.zOld + (value.z - value.zOld) * partialTick
            Gizmos.circle(Vec3(x, y, z), 0.55f, GizmoStyle.stroke(espColor.argb(), 2.5f))
        }
    }
}
