package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ccbluex.liquidbounce.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.LogUtil;
import top.fpsmaster.module.impl.optimization.NoHurtCam;
import top.fpsmaster.module.impl.optimization.Optimization;
import top.fpsmaster.module.impl.optimization.SmoothZoom;
import top.fpsmaster.module.impl.render.MinimizedBobbing;
import top.fpsmaster.module.impl.render.MotionBlur;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    private static final Identifier FPSMASTER_MOTION_BLUR = Identifier.fromNamespaceAndPath("fpsmaster", "motion_blur");

    @Shadow
    private void setPostEffect(Identifier identifier) {
    }

    @Shadow
    public abstract Identifier currentPostEffect();

    @Shadow
    public abstract void clearPostEffect();

    @Inject(method = "render", at = @At("HEAD"))
    public void hookGameRender(CallbackInfo callbackInfo) {
        if (MCEF.INSTANCE.isInitialized()) {
            try {
                MCEF.INSTANCE.getApp().getHandle().N_DoMessageLoopWork();
            } catch (Exception e) {
                LogUtil.logger.error("Failed to draw browser globally", e);
            }
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$cancelHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (NoHurtCam.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void fpsmaster$applyMotionBlur(CallbackInfo ci) {
        Identifier currentPostEffect = currentPostEffect();
        boolean ownsCurrentEffect = FPSMASTER_MOTION_BLUR.equals(currentPostEffect);
        boolean shouldUseMotionBlur = MotionBlur.isActive()
                && Minecraft.getInstance().level != null
                && Minecraft.getInstance().screen == null;

        if (shouldUseMotionBlur) {
            if (currentPostEffect == null || ownsCurrentEffect) {
                setPostEffect(FPSMASTER_MOTION_BLUR);
            }
        } else if (ownsCurrentEffect) {
            clearPostEffect();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$applySmoothZoom(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(SmoothZoom.modifyFov(cir.getReturnValueF()));
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void fpsmaster$resetOptimizationCounters(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        Optimization.resetEntityRenderCount();
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private <T> T shouldBob(OptionInstance<Boolean> optionInstance) {
        return (T) (Object) (optionInstance.get() && !MinimizedBobbing.Companion.getWorking());
    }
}
