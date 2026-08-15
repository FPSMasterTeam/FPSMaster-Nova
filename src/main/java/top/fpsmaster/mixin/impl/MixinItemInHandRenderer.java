package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(ItemInHandRenderer.class)
public class MixinItemInHandRenderer {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    public void onRenderArmWithItemHead(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equippedProgress, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, CallbackInfo ci) {
        if (!Animation.isActive()) {
            return;
        }

        if (Animation.Companion.getNoShield().getValue() && item.is(Items.SHIELD)) {
            ci.cancel();
            return;
        }

        poseStack.translate(
                Animation.Companion.getX().getValue(),
                Animation.Companion.getY().getValue(),
                Animation.Companion.getZ().getValue()
        );
        float scale = Animation.Companion.getScale().getValue().floatValue();
        poseStack.scale(scale, scale, scale);
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z", ordinal = 1))
    public void onRenderArmWithItem(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equippedProgress, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, CallbackInfo ci) {
        if (!Animation.isActive()) {
            return;
        }

        if (Animation.Companion.getOldRod().getValue() && (item.is(Items.FISHING_ROD) || item.is(Items.CARROT_ON_A_STICK))) {
            poseStack.translate(0.08F, -0.027F, -0.33F);
            poseStack.scale(0.93F, 1.0F, 1.0F);
        }
        if (Animation.Companion.getOldSwing().getValue() && swingProgress != 0.0F && !player.isUsingItem()) {
            poseStack.scale(0.85F, 0.85F, 0.85F);
            poseStack.translate(-0.06F, 0.003F, 0.05F);
        }
        if (Animation.Companion.getOldBow().getValue() && item.getUseAnimation() == ItemUseAnimation.BOW && player.isUsingItem() && player.getUsedItemHand() == hand) {
            poseStack.translate(-0.08F, 0.05F, 0.05F);
            poseStack.scale(0.92F, 0.92F, 0.92F);
        }
        if (Animation.Companion.getOldUsing().getValue() && player.isUsingItem() && player.getUsedItemHand() == hand) {
            poseStack.translate(0.04F, -0.02F, -0.04F);
        }
    }
}

//?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(ItemInHandRenderer.class)
public class MixinItemInHandRenderer {
    // 1.20.1 renderArmWithItem takes MultiBufferSource (not SubmitNodeCollector) and uses UseAnim.
    // All viewmodel transforms are applied at HEAD (classic old-animation approach) to avoid relying
    // on a version-specific mid-method anchor. void -> CallbackInfo.
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    public void fpsmaster$animation(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equippedProgress, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!Animation.isActive()) {
            return;
        }
        if (Animation.Companion.getNoShield().getValue() && item.is(Items.SHIELD)) {
            ci.cancel();
            return;
        }
        poseStack.translate(
                Animation.Companion.getX().getValue(),
                Animation.Companion.getY().getValue(),
                Animation.Companion.getZ().getValue()
        );
        float scale = Animation.Companion.getScale().getValue().floatValue();
        poseStack.scale(scale, scale, scale);
        if (Animation.Companion.getOldRod().getValue() && (item.is(Items.FISHING_ROD) || item.is(Items.CARROT_ON_A_STICK))) {
            poseStack.translate(0.08F, -0.027F, -0.33F);
            poseStack.scale(0.93F, 1.0F, 1.0F);
        }
        if (Animation.Companion.getOldSwing().getValue() && swingProgress != 0.0F && !player.isUsingItem()) {
            poseStack.scale(0.85F, 0.85F, 0.85F);
            poseStack.translate(-0.06F, 0.003F, 0.05F);
        }
        if (Animation.Companion.getOldBow().getValue() && item.getUseAnimation() == UseAnim.BOW && player.isUsingItem() && player.getUsedItemHand() == hand) {
            poseStack.translate(-0.08F, 0.05F, 0.05F);
            poseStack.scale(0.92F, 0.92F, 0.92F);
        }
        if (Animation.Companion.getOldUsing().getValue() && player.isUsingItem() && player.getUsedItemHand() == hand) {
            poseStack.translate(0.04F, -0.02F, -0.04F);
        }
    }
}

*///?}
