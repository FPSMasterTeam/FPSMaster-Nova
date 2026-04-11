package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(ItemInHandRenderer.class)
public class MixinItemInHandRenderer {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z", ordinal = 1))
    public void onRenderArmWithItem(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equippedProgress, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, CallbackInfo ci) {
        if (Animation.Companion.getOldRod().getValue() && (item.is(Items.FISHING_ROD) || item.is(Items.CARROT_ON_A_STICK))) {
            poseStack.translate(0.08F, -0.027F, -0.33F);
            poseStack.scale(0.93F, 1.0F, 1.0F);
        }
        if (Animation.Companion.getOldSwing().getValue() && swingProgress != 0.0F && !player.isUsingItem()) {
            poseStack.scale(0.85F, 0.85F, 0.85F);
            poseStack.translate(-0.06F, 0.003F, 0.05F);
        }
    }
}
