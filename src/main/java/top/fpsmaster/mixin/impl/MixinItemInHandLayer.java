package top.fpsmaster.mixin.impl;

// Four generations, because both the method name and the "is this arm using an item" source changed:
//   1.21.11+        submitArmWithItem(state, itemState, stack, arm, pose, SubmitNodeCollector, light);
//                   the render state carries ticksUsingItem(arm)
//   1.21.5..1.21.10 renderArmWithItem(state, itemState, arm, pose, MultiBufferSource, light); the render
//                   state has no use timer yet, so the per-arm HumanoidModel.ArmPose is the signal
//   1.20..1.21.4    renderArmWithItem(LivingEntity, ItemStack, ItemDisplayContext, arm, ...)
//   <1.20           the same, but ItemTransforms.TransformType instead of ItemDisplayContext
//? if >=1.21.11 {

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(ItemInHandLayer.class)
public class MixinItemInHandLayer {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void fpsmaster$oldThirdPerson(
            ArmedEntityRenderState state,
            ItemStackRenderState itemState,
            ItemStack stack,
            HumanoidArm arm,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!Animation.isActive() || !Animation.Companion.getOldThirdPerson().getValue()) {
            return;
        }
        if (state.ticksUsingItem(arm) <= 0.0F) {
            return;
        }
        poseStack.translate(0.05F, 0.0F, -0.1F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-50.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-60.0F));
    }
}

//?} else if >=1.21.5 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(ItemInHandLayer.class)
public class MixinItemInHandLayer {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void fpsmaster$oldThirdPerson(
            ArmedEntityRenderState state,
            ItemStackRenderState itemState,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!Animation.isActive() || !Animation.Companion.getOldThirdPerson().getValue()) {
            return;
        }
        HumanoidModel.ArmPose pose = arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
        if (pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM) {
            return;
        }
        poseStack.translate(0.05F, 0.0F, -0.1F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-50.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-60.0F));
    }
}

*///?} else if >=1.20 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(ItemInHandLayer.class)
public class MixinItemInHandLayer {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void fpsmaster$oldThirdPerson(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!Animation.isActive() || !Animation.Companion.getOldThirdPerson().getValue()) {
            return;
        }
        if (!entity.isUsingItem()) {
            return;
        }
        HumanoidArm using = entity.getMainArm();
        if (entity.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
            using = using.getOpposite();
        }
        if (using != arm) {
            return;
        }
        poseStack.translate(0.05F, 0.0F, -0.1F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-50.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-60.0F));
    }
}
*///?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(ItemInHandLayer.class)
public class MixinItemInHandLayer {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void fpsmaster$oldThirdPerson(
            LivingEntity entity,
            ItemStack stack,
            ItemTransforms.TransformType displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!Animation.isActive() || !Animation.Companion.getOldThirdPerson().getValue()) {
            return;
        }
        if (!entity.isUsingItem()) {
            return;
        }
        HumanoidArm using = entity.getMainArm();
        if (entity.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
            using = using.getOpposite();
        }
        if (using != arm) {
            return;
        }
        poseStack.translate(0.05F, 0.0F, -0.1F);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(-50.0F));
        poseStack.mulPose(Vector3f.XP.rotationDegrees(-10.0F));
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(-60.0F));
    }
}
*///?}
