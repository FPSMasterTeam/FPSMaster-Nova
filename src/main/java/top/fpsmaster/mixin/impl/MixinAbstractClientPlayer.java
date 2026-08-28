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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.cosmetic.CosmeticView;
import top.fpsmaster.module.impl.auxiliary.CustomFOV;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {
    //? if <1.21 {
    /*@Inject(method = "getCloakTextureLocation", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$customCapeTexture(CallbackInfoReturnable<net.minecraft.resources.ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        net.minecraft.resources.ResourceLocation selected = CosmeticView.capeTexture(player.getId());
        if (selected != null) cir.setReturnValue(selected);
    }

    @Inject(method = "getElytraTextureLocation", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$customElytraTexture(CallbackInfoReturnable<net.minecraft.resources.ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (!CosmeticView.rendersElytra(player.getId())) return;
        net.minecraft.resources.ResourceLocation selected = CosmeticView.wingTexture(player.getId());
        if (selected != null) cir.setReturnValue(selected);
    }
    *///?} else if <1.21.11 {
    /*@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$customCosmeticTextures(CallbackInfoReturnable<net.minecraft.client.resources.PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        int entityId = player.getId();
        net.minecraft.client.resources.PlayerSkin skin = cir.getReturnValue();
        net.minecraft.resources.ResourceLocation cape = CosmeticView.capeTexture(entityId);
        net.minecraft.resources.ResourceLocation elytra = CosmeticView.rendersElytra(entityId)
                ? CosmeticView.wingTexture(entityId) : skin.elytraTexture();
        if (cape != null || elytra != skin.elytraTexture()) {
            cir.setReturnValue(new net.minecraft.client.resources.PlayerSkin(
                    skin.texture(), skin.textureUrl(), cape == null ? skin.capeTexture() : cape,
                    elytra, skin.model(), skin.secure()
            ));
        }
    }
    *///?}
    @Redirect(
            method = "getFieldOfViewModifier",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean fpsmaster$disableFlyFov(Abilities abilities) {
        return !CustomFOV.isNoFlyFovEnabled() && abilities.flying;
    }

    //? if >=1.21 {
    @Redirect(
            method = "getFieldOfViewModifier",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D")
    )
    private double fpsmaster$disableSpeedFov(AbstractClientPlayer player, Holder<Attribute> attribute) {
        if (CustomFOV.isNoSpeedFovEnabled()) return player.getAbilities().getWalkingSpeed();
        return player.getAttributeValue(attribute);
    }
    //?} else {
    /*@Redirect(
            method = "getFieldOfViewModifier",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D")
    )
    private double fpsmaster$disableSpeedFov(AbstractClientPlayer player, Attribute attribute) {
        if (CustomFOV.isNoSpeedFovEnabled()) return player.getAbilities().getWalkingSpeed();
        return player.getAttributeValue(attribute);
    }
    *///?}

    // 26 把 ItemStack.is(Item) 换成了继承自 TypedInstance<T> 的 is(T)，字节码里擦除成
    // is(Ljava/lang/Object;)Z——@At 的 target 必须照字节码写，否则 Scanned 0 target(s)。
    //? if >=26 {
    /*@Redirect(
            method = "getFieldOfViewModifier",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
    )
    private boolean fpsmaster$disableBowFov(ItemStack stack, Object item) {
        if (CustomFOV.isNoBowFovEnabled() && item == Items.BOW) {
            return false;
        }
        return stack.is((Item) item);
    }
    *///?} else {
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
    //?}
}
