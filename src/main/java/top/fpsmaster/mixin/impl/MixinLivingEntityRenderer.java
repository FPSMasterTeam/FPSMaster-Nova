package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.mixin.interfaces.ILivingEntityRenderState;
import top.fpsmaster.module.impl.auxiliary.LevelTag;
import top.fpsmaster.module.impl.render.DamageIndicator;
import top.fpsmaster.module.impl.render.HitColor;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fpsmaster$captureLivingEntityId(LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        ((ILivingEntityRenderState) renderState).fpsmaster$setEntityId(entity.getId());
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfNameTag(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.shouldShowSelfNameTag()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                cir.setReturnValue(!entity.isInvisibleTo(player));
            }
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void fpsmaster$submitDamageIndicators(LivingEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (!DamageIndicator.isActive()) {
            return;
        }

        int entityId = ((ILivingEntityRenderState) renderState).fpsmaster$getEntityId();
        for (DamageIndicator.DisplayEntry entry : DamageIndicator.displayEntriesFor(entityId)) {
            nodeCollector.submitNameTag(
                    poseStack,
                    new Vec3(0.0, renderState.boundingBoxHeight + 0.35F + entry.getYOffset(), 0.0),
                    0,
                    Component.literal(entry.getText()).withColor(entry.getColor() & 0x00FFFFFF),
                    true,
                    renderState.lightCoords,
                    renderState.distanceToCameraSq,
                    cameraRenderState
            );
        }
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$replaceHitTint(LivingEntityRenderState renderState, CallbackInfoReturnable<Integer> cir) {
        if (HitColor.isActive() && renderState.hasRedOverlay) {
            cir.setReturnValue(HitColor.colorArgb());
        }
    }

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void fpsmaster$removeVanillaRedOverlay(LivingEntityRenderState renderState, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        if (HitColor.isActive() && renderState.hasRedOverlay) {
            cir.setReturnValue(OverlayTexture.NO_OVERLAY);
        }
    }
}
