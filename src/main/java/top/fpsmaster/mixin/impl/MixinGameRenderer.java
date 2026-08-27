package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ccbluex.liquidbounce.mcef.MCEF;
import net.minecraft.client.renderer.GameRenderer;
//? if >=1.21.5 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Redirect;
//?}
// The post-effect id type was renamed ResourceLocation -> Identifier in 1.21.11, and @Shadow
// signatures cannot be aliased, so the post-effect members are declared once per name.
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else if >=1.21.5 {
/*import net.minecraft.resources.ResourceLocation;
*///?}
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
import java.io.IOException;
*///?}

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    //? if >=1.21.11 {
    private static final Identifier FPSMASTER_MOTION_BLUR = Identifier.fromNamespaceAndPath("fpsmaster", "motion_blur");
    private static final Identifier FPSMASTER_MOTION_BLUR_FAST = Identifier.fromNamespaceAndPath("fpsmaster", "motion_blur_fast");

    @Shadow
    private void setPostEffect(Identifier identifier) {
    }

    @Shadow
    public abstract Identifier currentPostEffect();

    @Shadow
    public abstract void clearPostEffect();
    //?} else if >=1.21.5 {
    /*private static final ResourceLocation FPSMASTER_MOTION_BLUR = ResourceLocation.fromNamespaceAndPath("fpsmaster", "motion_blur");
    private static final ResourceLocation FPSMASTER_MOTION_BLUR_FAST = ResourceLocation.fromNamespaceAndPath("fpsmaster", "motion_blur_fast");

    @Shadow
    private void setPostEffect(ResourceLocation identifier) {
    }

    @Shadow
    public abstract ResourceLocation currentPostEffect();

    @Shadow
    public abstract void clearPostEffect();
    *///?}

    @Inject(method = "render", at = @At("HEAD"))
    public void hookGameRender(CallbackInfo callbackInfo) {
        top.fpsmaster.ui.kit.NovaBlur.INSTANCE.beginFrame();
        if (MCEF.INSTANCE.isInitialized()) {
            try {
                // On Windows/Linux CEF runs on its own message-loop thread (mcef-nova's
                // CefMessageLoopThread) and this only uploads the frames that thread buffered —
                // the render thread never blocks in CefDoMessageLoopWork again (that block, ~13ms
                // per frame while the browser was idle, was the "40 fps until the ClickGUI opens"
                // Windows bug). On macOS this still pumps CEF on this thread, then uploads.
                MCEF.INSTANCE.update();
            } catch (Exception e) {
                LogUtil.logger.error("Failed to update browser frames", e);
            }
            //? if >=1.21.5 {
            // mcef still performs residual raw GL on this thread on the legacy (<1.21.5) upload
            // path and in scheduled renderer create/close tasks; those calls bypass
            // GlStateManager. Realign the state cache with GL reality before the frame renders,
            // or the next lazily-baked font glyph is uploaded through a stale "cache hit" bind and
            // lands in the browser texture instead of the glyph atlas (glyphs go permanently blank).
            top.fpsmaster.web.cef.ExternalGlStateSync.resync();
            //?}
        }
    }

    //? if >=26 {
    /*@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$cancelHurtCam(
            net.minecraft.client.renderer.state.level.CameraRenderState camera,
            PoseStack poseStack,
            CallbackInfo ci
    ) {
        if (NoHurtCam.isActive()) ci.cancel();
    }
    *///?} else {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$cancelHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (NoHurtCam.isActive()) ci.cancel();
    }
    //?}

    //? if >=1.21.5 {
    @Inject(method = "tick", at = @At("TAIL"))
    private void fpsmaster$applyMotionBlur(CallbackInfo ci) {
        var currentPostEffect = currentPostEffect();
        boolean ownsCurrentEffect = FPSMASTER_MOTION_BLUR.equals(currentPostEffect)
                || FPSMASTER_MOTION_BLUR_FAST.equals(currentPostEffect);
        boolean shouldUseMotionBlur = MotionBlur.isActive()
                && Minecraft.getInstance().level != null
                //? if >=26 {
                /*&& Minecraft.getInstance().gui.screen() == null;
                *///?} else {
                && Minecraft.getInstance().screen == null;
                //?}

        if (shouldUseMotionBlur) {
            var desiredEffect = MotionBlur.useFastChain() ? FPSMASTER_MOTION_BLUR_FAST : FPSMASTER_MOTION_BLUR;
            if ((currentPostEffect == null || ownsCurrentEffect) && !desiredEffect.equals(currentPostEffect)) {
                setPostEffect(desiredEffect);
            }
        } else if (ownsCurrentEffect) {
            clearPostEffect();
        }
    }
    //?} else {

    /*@Unique
    private static final ResourceLocation FPSMASTER_MOTION_BLUR_1201 = ResourceLocation.tryBuild("fpsmaster", "shaders/post/fpsmaster_motion_blur.json");

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
    }

    *///?}

    //? if >=1.21.5 && <= 1.21.11 {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$applySmoothZoom(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (useFovSetting) cir.setReturnValue(SmoothZoom.modifyFov(cir.getReturnValueF()));
    }
    //?} else if <= 1.21.11 {
    /*@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$applySmoothZoom(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (useFovSetting) cir.setReturnValue((double) SmoothZoom.modifyFov((float) (double) cir.getReturnValueD()));
    }
    *///?}

    //? if >=1.21.5 {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void fpsmaster$resetOptimizationCounters(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        Optimization.resetEntityRenderCount();
    }
    //?} else {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void fpsmaster$resetOptimizationCounters(CallbackInfo ci) {
        Optimization.resetEntityRenderCount();
    }
    *///?}

    //? if >=26 {
    /*@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$cancelViewBob(
            net.minecraft.client.renderer.state.level.CameraRenderState camera,
            PoseStack poseStack,
            CallbackInfo ci
    ) {
        if (MinimizedBobbing.Companion.getWorking()) {
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.mixin("game-renderer");
                top.fpsmaster.diagnostics.Smoke.feature("minimized-bobbing");
            }
            ci.cancel();
        }
    }
    *///?} else if >=1.21.5 {
    @SuppressWarnings("unchecked")
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private <T> T shouldBob(OptionInstance<Boolean> optionInstance) {
        return (T) (Object) (optionInstance.get() && !MinimizedBobbing.Companion.getWorking());
    }
    //?}
}
