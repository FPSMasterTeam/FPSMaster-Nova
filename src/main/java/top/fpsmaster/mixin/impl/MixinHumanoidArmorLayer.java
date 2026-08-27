package top.fpsmaster.mixin.impl;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21.8 {

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(HumanoidArmorLayer.class)
public class MixinHumanoidArmorLayer {
    static boolean fpsmaster$hurt;
    //? if >=1.21.11 {
    @Inject(method = "submit", at = @At("HEAD"))
    private void fpsmaster$capture(
            PoseStack poseStack,
            net.minecraft.client.renderer.SubmitNodeCollector nodeCollector,
            int packedLight,
            HumanoidRenderState state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        fpsmaster$hurt = Animation.isActive()
                && Animation.Companion.getOldDamage().getValue()
                && state.hasRedOverlay;
    }
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void fpsmaster$capture(
            PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLight,
            HumanoidRenderState state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        fpsmaster$hurt = Animation.isActive()
                && Animation.Companion.getOldDamage().getValue()
                && state.hasRedOverlay;
    }
    *///?}
}

//?} else {

/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(HumanoidArmorLayer.class)
public class MixinHumanoidArmorLayer {
    @Unique
    private static LivingEntity fpsmaster$entity;

    @Inject(method = "renderArmorPiece", at = @At("HEAD"))
    private void fpsmaster$capture(
            PoseStack poseStack,
            MultiBufferSource buffer,
            LivingEntity entity,
            EquipmentSlot slot,
            int packedLight,
            HumanoidModel<?> model,
            CallbackInfo ci
    ) {
        fpsmaster$entity = entity;
    }

    @Redirect(
            method = "renderModel",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I")
    )
    private int fpsmaster$oldDamageOverlay() {
        LivingEntity entity = fpsmaster$entity;
        if (entity != null
                && Animation.isActive()
                && Animation.Companion.getOldDamage().getValue()
                && (entity.hurtTime > 0 || entity.deathTime > 0)) {
            return OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(true));
        }
        return OverlayTexture.NO_OVERLAY;
    }
}
*///?}
