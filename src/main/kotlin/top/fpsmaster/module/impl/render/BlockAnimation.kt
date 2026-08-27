package top.fpsmaster.module.impl.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.item.ItemStack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** 1.7-style extra first-person block pose, stacked on vanilla item-in-hand. */
object BlockAnimation {
    @JvmStatic
    fun apply(
        pose: PoseStack,
        equipped: Float,
        swing: Float,
        sneaking: Boolean,
        swinging: Boolean,
        stack: ItemStack
    ) {
        if (!Animation.oldBlocking.getValue() || swing <= 0f || !isSword(stack)) {
            return
        }

        val s = sin(sqrt(swing) * PI.toFloat())
        val e = sin(sqrt(equipped) * PI.toFloat())
        block(pose)
        when (Animation.animationMode.getValue()) {
            "lunar" -> {
                move(pose, 0.07f, -0.14f, -0.11f)
                move(pose, -0.5f, 0.2f, 0f)
            }
            "sigma" -> {
                rotate(pose, -e * 27.5f, -8f, 0f, 9f)
                rotate(pose, -e * 45f, 1f, e / 2f, 0f)
                move(pose, 1.2f, 0.3f, 0.5f)
                move(pose, -1f, if (sneaking) -0.1f else -0.2f, 0.2f)
            }
            "debug" -> move(pose, -0.5f, 0.2f, 0f)
            "luna" -> {
                move(pose, -0.2f, 0.45f, 0.25f)
                rotate(pose, -s * 20f, -5f, -5f, 9f)
            }
            "swang" -> {
                rotate(pose, s * 15f, -s, 0f, 9f)
                rotate(pose, s * 40f, 1f, -s / 2f, 0f)
            }
            "swank" -> {
                rotate(pose, e * 30f, -e, 0f, 9f)
                rotate(pose, e * 40f, 1f, -e, 0f)
            }
            "swong" -> {
                rotate(pose, -s * 20f, s / 2f, 0f, 9f)
                rotate(pose, -s * 30f, 1f, s / 2f, 0f)
            }
            "jigsaw" -> move(pose, -0.5f, 0f, 0f)
            "jello" -> {
                move(pose, 0.3f, 0f, 0.4f)
                move(pose, 0f, 0.5f, 0f)
                rotate(pose, 90f, 1f, 0f, -1f)
                move(pose, 0.6f, 0.5f, 0f)
                rotate(pose, -90f, 1f, 0f, -1f)
                rotate(pose, -10f, 1f, 0f, -1f)
                // Edge reflects the 255ms ramp into a triangle wave before doubling it, so the
                // rotation eases back instead of snapping at the wrap. See Edge MixinItemRenderer "Jello".
                val phase = System.currentTimeMillis() % 255L
                val wave = if (phase > 127L) 255L - phase else phase
                val pulse = (wave * 2L).coerceAtMost(255L).toFloat()
                rotate(pose, if (swinging) -pulse / 5f else 1f, 1f, 0f, 1f)
            }
            "push" -> {
                rotate(pose, -s * 35f, -8f, 0f, 9f)
                rotate(pose, -s * 10f, 1f, -0.4f, -0.5f)
            }
        }
    }

    private fun isSword(stack: ItemStack): Boolean {
        //? if >=1.20.5 {
        return stack.`is`(net.minecraft.tags.ItemTags.SWORDS)
        //?} else {
        /*return stack.item is net.minecraft.world.item.SwordItem*/
        //?}
    }

    private fun block(pose: PoseStack) {
        move(pose, -0.5f, 0.2f, 0f)
        rotate(pose, 30f, 0f, 1f, 0f)
        rotate(pose, -80f, 1f, 0f, 0f)
        rotate(pose, 60f, 0f, 1f, 0f)
    }

    private fun move(pose: PoseStack, x: Float, y: Float, z: Float) {
        //? if >=1.21.5 {
        pose.translate(x, y, z)
        //?} else {
        /*pose.translate(x.toDouble(), y.toDouble(), z.toDouble())*/
        //?}
    }

    private fun rotate(pose: PoseStack, degrees: Float, x: Float, y: Float, z: Float) {
        val len = sqrt(x * x + y * y + z * z)
        if (len < 1e-6f) {
            return
        }
        val nx = x / len
        val ny = y / len
        val nz = z / len
        //? if >=1.20 {
        pose.mulPose(org.joml.Quaternionf().rotationAxis(Math.toRadians(degrees.toDouble()).toFloat(), nx, ny, nz))
        //?} else {
        /*pose.mulPose(com.mojang.math.Vector3f(nx, ny, nz).rotationDegrees(degrees))*/
        //?}
    }
}
