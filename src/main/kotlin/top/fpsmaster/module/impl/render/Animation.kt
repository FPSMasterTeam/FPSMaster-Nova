package top.fpsmaster.module.impl.render

import io.github.vlouboos.standaloneevent.api.EventHandler
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class Animation : Module("animation", Category.RENDER) {
    companion object {
        val oldBackward = OptionValue("old-backward", false)
        val oldBlocking = OptionValue("old-blocking", true)
        val animationMode = ChoiceValue(
            "animation-mode",
            listOf("lunar", "1.7", "swang", "sigma", "swank", "swong", "debug", "luna", "jigsaw", "jello", "push")
        ) { oldBlocking.getValue() }
        val oldSwing = OptionValue("old-swing", true)
        val oldRod = OptionValue("old-rod", true)
        val noShield = OptionValue("no-shield", true)
        val animationSneak = OptionValue("animation-sneak", true)
        val oldBow = OptionValue("old-bow", true)
        val oldUsing = OptionValue("old-using", true)
        val blockSwing = OptionValue("block-swing", true)
        val oldDamage = OptionValue("old-damage", true)
        val oldThirdPerson = OptionValue("old-third-person", true)
        val x = NumberValue("x", 0.0, -1.0, 1.0, 0.01)
        val y = NumberValue("y", 0.0, -1.0, 1.0, 0.01)
        val z = NumberValue("z", 0.0, -1.0, 1.0, 0.01)
        val scale = NumberValue("scale", 1.0, 0.0, 3.0, 0.01)

        private var active = false

        /**
         * Set by MixinHumanoidArmorLayer at the start of an armour layer, read by
         * MixinEquipmentLayerRenderer while that same layer renders. It cannot live as a static field on
         * the mixin itself: Mixin rejects non-private static fields in a mixin class outright, which
         * fails the whole apply and aborts the initial resource reload.
         */
        @JvmStatic
        var armorHurtOverlay: Boolean = false

        @JvmStatic
        fun isActive(): Boolean = active
    }

    init {
        values.addAll(
            arrayOf(
                oldBackward,
                oldBlocking,
                animationMode,
                oldSwing,
                oldRod,
                noShield,
                animationSneak,
                oldBow,
                oldUsing,
                blockSwing,
                oldDamage,
                oldThirdPerson,
                x,
                y,
                z,
                scale
            )
        )
    }

    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        if (!isActive() || !blockSwing.getValue()) {
            return
        }
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (!minecraft.options.keyAttack.isDown || !player.isUsingItem) {
            return
        }
        val hit = minecraft.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) {
            return
        }
        player.swing(player.usedItemHand)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }
}
