package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ccbluex.liquidbounce.mcef.MCEF;
import net.minecraft.client.renderer.GameRenderer;
//? if >=1.21.5 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Redirect;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.LogUtil;
import top.fpsmaster.module.impl.optimization.NoHurtCam;
import top.fpsmaster.module.impl.optimization.Optimization;
import top.fpsmaster.module.impl.optimization.SmoothZoom;
//? if >=1.21.5 {
import top.fpsmaster.module.impl.render.MinimizedBobbing;
import top.fpsmaster.module.impl.render.MotionBlur;
//?}
//? if <1.21.5 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Unique;
import top.fpsmaster.module.impl.render.MotionBlur;
import java.io.IOException;*/
//?}

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    //? if >=1.21.5 {
    private static final Identifier FPSMASTER_MOTION_BLUR = Identifier.fromNamespaceAndPath("fpsmaster", "motion_blur");
    private static final Identifier FPSMASTER_MOTION_BLUR_FAST = Identifier.fromNamespaceAndPath("fpsmaster", "motion_blur_fast");

    @Shadow
    private void setPostEffect(Identifier identifier) {
    }

    @Shadow
    public abstract Identifier currentPostEffect();

    @Shadow
    public abstract void clearPostEffect();
    //?}

    @Inject(method = "render", at = @At("HEAD"))
    public void hookGameRender(CallbackInfo callbackInfo) {
        if (MCEF.INSTANCE.isInitialized()) {
            try {
                MCEF.INSTANCE.getApp().getHandle().N_DoMessageLoopWork();
            } catch (Exception e) {
                LogUtil.logger.error("Failed to draw browser globally", e);
            }
            //? if >=1.21.5 {
            // DoMessageLoopWork pumps CEF on THIS (render) thread — including onPaint, where the
            // Minecraft-agnostic mcef uploads the browser frame with raw GL calls that bypass
            // GlStateManager. Realign the state cache with GL reality before the frame renders,
            // or the next lazily-baked font glyph is uploaded through a stale "cache hit" bind and
            // lands in the browser texture instead of the glyph atlas (glyphs go permanently blank).
            top.fpsmaster.web.cef.ExternalGlStateSync.resync();
            //?}
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$cancelHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (NoHurtCam.isActive()) {
            ci.cancel();
        }
    }

    //? if >=1.21.5 {
    @Inject(method = "tick", at = @At("TAIL"))
    private void fpsmaster$applyMotionBlur(CallbackInfo ci) {
        Identifier currentPostEffect = currentPostEffect();
        boolean ownsCurrentEffect = FPSMASTER_MOTION_BLUR.equals(currentPostEffect)
                || FPSMASTER_MOTION_BLUR_FAST.equals(currentPostEffect);
        boolean shouldUseMotionBlur = MotionBlur.isActive()
                && Minecraft.getInstance().level != null
                && Minecraft.getInstance().screen == null;

        if (shouldUseMotionBlur) {
            Identifier desiredEffect = MotionBlur.useFastChain() ? FPSMASTER_MOTION_BLUR_FAST : FPSMASTER_MOTION_BLUR;
            if ((currentPostEffect == null || ownsCurrentEffect) && !desiredEffect.equals(currentPostEffect)) {
                setPostEffect(desiredEffect);
            }
        } else if (ownsCurrentEffect) {
            clearPostEffect();
        }
    }
    //?} else {

    /*@Unique
    private static final ResourceLocation FPSMASTER_MOTION_BLUR_1201 = new ResourceLocation("fpsmaster", "shaders/post/fpsmaster_motion_blur.json");

    @Unique
    private PostChain fpsmaster$motionBlur;

    @Unique
    private int fpsmaster$motionBlurWidth = -1;

    @Unique
    private int fpsmaster$motionBlurHeight = -1;

    // 1.20.1 motion blur: drive a legacy PostChain built from the vanilla "phosphor" feedback program
    // (the same accumulation the 1.21.5 RenderPipeline effect uses). Process it right after the world
    // is drawn, before the GUI, so the main render target gets the trail effect.
    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER)
    )
    private void fpsmaster$applyMotionBlur(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!MotionBlur.isActive() || minecraft.level == null || minecraft.screen != null) {
            fpsmaster$closeMotionBlur();
            return;
        }

        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        if (fpsmaster$motionBlur == null) {
            try {
                fpsmaster$motionBlur = new PostChain(
                        minecraft.getTextureManager(),
                        minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(),
                        FPSMASTER_MOTION_BLUR_1201
                );
                fpsmaster$motionBlur.resize(width, height);
                fpsmaster$motionBlurWidth = width;
                fpsmaster$motionBlurHeight = height;
            } catch (IOException exception) {
                LogUtil.logger.error("Failed to load FPSMaster motion blur post chain", exception);
                fpsmaster$motionBlur = null;
                return;
            }
        } else if (width != fpsmaster$motionBlurWidth || height != fpsmaster$motionBlurHeight) {
            fpsmaster$motionBlur.resize(width, height);
            fpsmaster$motionBlurWidth = width;
            fpsmaster$motionBlurHeight = height;
        }

        float factor = MotionBlur.factor();
        java.util.List<PostPass> passes = ((MixinPostChain) (Object) fpsmaster$motionBlur).fpsmaster$getPasses();
        if (!passes.isEmpty()) {
            passes.get(0).getEffect().safeGetUniform("Phosphor").set(factor, factor, factor);
        }

        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.resetTextureMatrix();
        fpsmaster$motionBlur.process(partialTicks);
        minecraft.getMainRenderTarget().bindWrite(true);
    }

    @Unique
    private void fpsmaster$closeMotionBlur() {
        if (fpsmaster$motionBlur != null) {
            fpsmaster$motionBlur.close();
            fpsmaster$motionBlur = null;
            fpsmaster$motionBlurWidth = -1;
            fpsmaster$motionBlurHeight = -1;
        }
    }*/

    //?}

    //? if >=1.21.5 {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$applySmoothZoom(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(SmoothZoom.modifyFov(cir.getReturnValueF()));
    }
    //?} else {
    /*@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$applySmoothZoom(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue((double) SmoothZoom.modifyFov((float) (double) cir.getReturnValueD()));
    }*/
    //?}

    //? if >=1.21.5 {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void fpsmaster$resetOptimizationCounters(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        Optimization.resetEntityRenderCount();
    }
    //?} else {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void fpsmaster$resetOptimizationCounters(CallbackInfo ci) {
        Optimization.resetEntityRenderCount();
    }*/
    //?}

    //? if >=1.21.5 {
    @SuppressWarnings("unchecked")
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private <T> T shouldBob(OptionInstance<Boolean> optionInstance) {
        return (T) (Object) (optionInstance.get() && !MinimizedBobbing.Companion.getWorking());
    }
    //?}
}
