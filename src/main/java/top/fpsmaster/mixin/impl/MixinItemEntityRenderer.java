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

//?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.ItemPhysics;

@Mixin(ItemEntityRenderer.class)
public class MixinItemEntityRenderer {
    // 1.20.1 has no render-state extraction: rotate the dropped item in render() directly, right
    // after vanilla applies its spin (entity.getSpin(F)F), to lay it flat on the ground.
    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;getSpin(F)F", shift = At.Shift.AFTER)
    )
    private void fpsmaster$layDroppedItemsFlat(ItemEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (ItemPhysics.isActive()) {
            float rotationPitch = entity.onGround() ? 0.0F : (entity.tickCount + partialTicks) * 11.0F;
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotationPitch));
            if (!entity.onGround()) {
                poseStack.translate(0.0F, 0.0F, -0.04F);
            }
        }
    }
}

*///?}
