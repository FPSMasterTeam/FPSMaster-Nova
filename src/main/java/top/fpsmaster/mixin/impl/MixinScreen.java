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

//?}
