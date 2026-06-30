package top.fpsmaster.module.impl.ui

import net.minecraft.client.Minecraft
// The Gizmos debug-shape API is 1.21.11+; 1.21.8 has neither it nor the legacy immediate-mode path,
// so the target ring is simply not drawn on 1.21.8 (acceptable gap, no crash).
//? if >=1.21.11 {
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
//?}
//? if <1.21.5 {
/*import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
*///?}
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
            //? if >=1.21.11 {
            Gizmos.circle(Vec3(x, y, z), 0.55f, GizmoStyle.stroke(espColor.argb(), 2.5f))
            //?}
        }

        //? if <1.21.5 {
        /*@JvmStatic
        fun renderTargetEsp1201(
            poseStack: PoseStack,
            bufferSource: MultiBufferSource.BufferSource,
            camX: Double,
            camY: Double,
            camZ: Double,
            partialTick: Float
        ) {
            if (!active || targetEsp.getValue().toInt() != 0) {
                return
            }

            val value = activeTarget(3_000L, clearWhenExpired = false) ?: return
            val x = value.xOld + (value.x - value.xOld) * partialTick
            val y = value.yOld + (value.y - value.yOld) * partialTick
            val z = value.zOld + (value.z - value.zOld) * partialTick

            val argb = espColor.argb()
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f

            val box = value.boundingBox.move(-value.x, -value.y, -value.z)
            poseStack.pushPose()
            poseStack.translate(x - camX, y - camY, z - camZ)
            LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), box, r, g, b, a)
            poseStack.popPose()
            bufferSource.endBatch(RenderType.lines())
        }
        *///?}
    }
}
