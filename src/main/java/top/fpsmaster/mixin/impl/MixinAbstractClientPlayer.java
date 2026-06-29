package top.fpsmaster.mixin.impl;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.auxiliary.CustomFOV;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {
    @Redirect(
            method = "getFieldOfViewModifier",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean fpsmaster$disableFlyFov(Abilities abilities) {
        return !CustomFOV.isNoFlyFovEnabled() && abilities.flying;
    }

    //? if >=1.21.5 {
    @Redirect(
            method = "getFieldOfViewModifier",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"
            )
    )
    private double fpsmaster$disableSpeedFov(AbstractClientPlayer player, Holder<Attribute> attribute) {
        if (CustomFOV.isNoSpeedFovEnabled()) {
            return player.getAbilities().getWalkingSpeed();
        }
        return player.getAttributeValue(attribute);
    }
    //?} else {
    /*@Redirect(
            method = "getFieldOfViewModifier",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D"
            )
    )
    private double fpsmaster$disableSpeedFov(AbstractClientPlayer player, Attribute attribute) {
        if (CustomFOV.isNoSpeedFovEnabled()) {
            return player.getAbilities().getWalkingSpeed();
        }
        return player.getAttributeValue(attribute);
    }*/
    //?}

    @Redirect(
            method = "getFieldOfViewModifier",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
    )
    private boolean fpsmaster$disableBowFov(ItemStack stack, Item item) {
        if (CustomFOV.isNoBowFovEnabled() && item == Items.BOW) {
            return false;
        }
        return stack.is(item);
    }
}
