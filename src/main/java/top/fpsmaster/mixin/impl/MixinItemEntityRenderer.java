package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.mixin.interfaces.IItemEntityRenderState;
import top.fpsmaster.module.impl.render.ItemPhysics;

@Mixin(ItemEntityRenderer.class)
public class MixinItemEntityRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fpsmaster$captureItemPhysicsState(ItemEntity itemEntity, ItemEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        float rotationPitch = itemEntity.onGround() ? 0.0F : (itemEntity.tickCount + partialTick) * 11.0F;
        ((IItemEntityRenderState) renderState).fpsmaster$setItemPhysicsState(itemEntity.onGround(), rotationPitch);
    }

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;getSpin(FF)F"))
    private void fpsmaster$layDroppedItemsFlat(ItemEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (ItemPhysics.isActive()) {
            IItemEntityRenderState itemPhysicsState = (IItemEntityRenderState) renderState;
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(itemPhysicsState.fpsmaster$getRotationPitch()));
            if (!itemPhysicsState.fpsmaster$isOnGround()) {
                poseStack.translate(0.0F, 0.0F, -0.04F);
            }
        }
    }
}

//?}
