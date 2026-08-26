package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.BetterScreen;
import top.fpsmaster.ui.MainMenuBackgroundRenderer;

@Mixin(Screen.class)
public abstract class MixinScreen {
    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    public abstract void renderTransparentBackground(GuiGraphics guiGraphics);

    @Shadow
    protected abstract void renderBlurredBackground(GuiGraphics guiGraphics);

    @Unique
    private float fpsmaster$backgroundAlpha;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$renderBetterScreenBackground(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        if (Minecraft.getInstance().level == null || !BetterScreen.isActive()) {
            fpsmaster$backgroundAlpha = 0.0F;
            return;
        }

        if (!BetterScreen.background.getValue()) {
            if (BetterScreen.blur.getValue()) {
                renderBlurredBackground(guiGraphics);
            }
            renderTransparentBackground(guiGraphics);
            ci.cancel();
            return;
        }

        if (BetterScreen.blur.getValue()) {
            renderBlurredBackground(guiGraphics);
        }

        if (BetterScreen.backgroundAnimation.getValue()) {
            fpsmaster$backgroundAlpha += (170.0F - fpsmaster$backgroundAlpha) * 0.2F;
        } else {
            fpsmaster$backgroundAlpha = 170.0F;
        }

        int alpha = Math.max(0, Math.min(170, Math.round(fpsmaster$backgroundAlpha)));
        int topColor = alpha << 24;
        int bottomColor = (alpha << 24) | 0x303030;
        guiGraphics.fillGradient(0, 0, width, height, topColor, bottomColor);
        ci.cancel();
    }

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$renderConfiguredTitleBackground(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof TitleScreen) || MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) {
            return;
        }

        MainMenuBackgroundRenderer.render(guiGraphics, width, height, partialTick);
        ci.cancel();
    }
}

//?} else if >=1.20 {

/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.LogUtil;
import top.fpsmaster.module.impl.ui.BetterScreen;

import java.io.IOException;

@Mixin(Screen.class)
public abstract class MixinScreen {
    @Shadow
    public int width;

    @Shadow
    public int height;

    @Unique
    private static PostChain fpsmaster$blurChain;

    @Unique
    private static int fpsmaster$blurWidth = -1;

    @Unique
    private static int fpsmaster$blurHeight = -1;

    @Unique
    private float fpsmaster$backgroundAlpha;

    // 1.20.1 Screen.renderBackground(GuiGraphics) only draws the in-world dim or the dirt texture.
    // Re-implement the BetterScreen background + a vanilla "blur" PostChain applied to the live world
    // framebuffer behind the screen.
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$renderBetterScreenBackground(GuiGraphics guiGraphics, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !BetterScreen.isActive()) {
            fpsmaster$backgroundAlpha = 0.0F;
            return;
        }

        if (BetterScreen.blur.getValue()) {
            fpsmaster$renderBlur(minecraft, guiGraphics);
        }

        if (!BetterScreen.background.getValue()) {
            guiGraphics.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
            ci.cancel();
            return;
        }

        if (BetterScreen.backgroundAnimation.getValue()) {
            fpsmaster$backgroundAlpha += (170.0F - fpsmaster$backgroundAlpha) * 0.2F;
        } else {
            fpsmaster$backgroundAlpha = 170.0F;
        }

        int alpha = Math.max(0, Math.min(170, Math.round(fpsmaster$backgroundAlpha)));
        int topColor = alpha << 24;
        int bottomColor = (alpha << 24) | 0x303030;
        guiGraphics.fillGradient(0, 0, this.width, this.height, topColor, bottomColor);
        ci.cancel();
    }

    @Unique
    private static boolean fpsmaster$blurUnavailable = false;

    @Unique
    private void fpsmaster$renderBlur(Minecraft minecraft, GuiGraphics guiGraphics) {
        // If the blur post chain failed to load once, never retry (it was spamming the log every
        // frame) and never let a blur problem affect the screen — just skip the effect.
        if (fpsmaster$blurUnavailable) {
            return;
        }
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        if (fpsmaster$blurChain == null) {
            try {
                fpsmaster$blurChain = new PostChain(
                        minecraft.getTextureManager(),
                        minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(),
                        ResourceLocation.tryBuild("fpsmaster", "shaders/post/fpsmaster_blur.json")
                );
                fpsmaster$blurChain.resize(width, height);
                fpsmaster$blurWidth = width;
                fpsmaster$blurHeight = height;
            } catch (Throwable exception) {
                LogUtil.logger.error("Failed to load FPSMaster screen blur post chain; disabling blur", exception);
                fpsmaster$blurChain = null;
                fpsmaster$blurUnavailable = true;
                return;
            }
        } else if (width != fpsmaster$blurWidth || height != fpsmaster$blurHeight) {
            fpsmaster$blurChain.resize(width, height);
            fpsmaster$blurWidth = width;
            fpsmaster$blurHeight = height;
        }

        try {
            guiGraphics.flush();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            fpsmaster$blurChain.process(0f);
        } catch (Throwable exception) {
            LogUtil.logger.error("FPSMaster screen blur failed; disabling blur", exception);
            fpsmaster$blurUnavailable = true;
        } finally {
            minecraft.getMainRenderTarget().bindWrite(false);
        }
    }
}

*///?} else {

/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.LogUtil;
import top.fpsmaster.module.impl.ui.BetterScreen;

// 1.19.2 predates GuiGraphics. Screen.renderBackground(PoseStack) only forwards to the
// renderBackground(PoseStack, int) overload, which is where the in-world dim / dirt texture is actually
// drawn, so that overload is the injection target and both entry points are covered. Drawing goes
// through the compat GuiGraphics shim; there is no Screen.renderPanorama on 1.19.2 (the title-screen
// background is handled by MixinTitleScreenBackground instead).
@Mixin(Screen.class)
public abstract class MixinScreen {
    @Shadow
    public int width;

    @Shadow
    public int height;

    @Unique
    private static PostChain fpsmaster$blurChain;

    @Unique
    private static int fpsmaster$blurWidth = -1;

    @Unique
    private static int fpsmaster$blurHeight = -1;

    @Unique
    private static boolean fpsmaster$blurUnavailable = false;

    @Unique
    private float fpsmaster$backgroundAlpha;

    @Inject(method = "renderBackground(Lcom/mojang/blaze3d/vertex/PoseStack;I)V", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$renderBetterScreenBackground(PoseStack poseStack, int vOffset, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !BetterScreen.isActive()) {
            fpsmaster$backgroundAlpha = 0.0F;
            return;
        }

        if (BetterScreen.blur.getValue()) {
            fpsmaster$renderBlur(minecraft);
        }

        top.fpsmaster.compat.GuiGraphics guiGraphics = new top.fpsmaster.compat.GuiGraphics(poseStack);
        if (!BetterScreen.background.getValue()) {
            guiGraphics.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
            ci.cancel();
            return;
        }

        if (BetterScreen.backgroundAnimation.getValue()) {
            fpsmaster$backgroundAlpha += (170.0F - fpsmaster$backgroundAlpha) * 0.2F;
        } else {
            fpsmaster$backgroundAlpha = 170.0F;
        }

        int alpha = Math.max(0, Math.min(170, Math.round(fpsmaster$backgroundAlpha)));
        int topColor = alpha << 24;
        int bottomColor = (alpha << 24) | 0x303030;
        guiGraphics.fillGradient(0, 0, this.width, this.height, topColor, bottomColor);
        ci.cancel();
    }

    @Unique
    private void fpsmaster$renderBlur(Minecraft minecraft) {
        // If the blur post chain failed to load once, never retry (it was spamming the log every
        // frame) and never let a blur problem affect the screen — just skip the effect.
        if (fpsmaster$blurUnavailable) {
            return;
        }
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        if (fpsmaster$blurChain == null) {
            try {
                fpsmaster$blurChain = new PostChain(
                        minecraft.getTextureManager(),
                        minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(),
                        new ResourceLocation("fpsmaster", "shaders/post/fpsmaster_blur.json")
                );
                fpsmaster$blurChain.resize(width, height);
                fpsmaster$blurWidth = width;
                fpsmaster$blurHeight = height;
            } catch (Throwable exception) {
                LogUtil.logger.error("Failed to load FPSMaster screen blur post chain; disabling blur", exception);
                fpsmaster$blurChain = null;
                fpsmaster$blurUnavailable = true;
                return;
            }
        } else if (width != fpsmaster$blurWidth || height != fpsmaster$blurHeight) {
            fpsmaster$blurChain.resize(width, height);
            fpsmaster$blurWidth = width;
            fpsmaster$blurHeight = height;
        }

        try {
            // No batched GuiGraphics buffer source to flush on 1.19.2 (drawing is immediate mode).
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            fpsmaster$blurChain.process(minecraft.getDeltaFrameTime());
        } catch (Throwable exception) {
            LogUtil.logger.error("FPSMaster screen blur failed; disabling blur", exception);
            fpsmaster$blurUnavailable = true;
        } finally {
            minecraft.getMainRenderTarget().bindWrite(false);
        }
    }
}

*///?}
