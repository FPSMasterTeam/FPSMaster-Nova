package top.fpsmaster.mixin.impl;

// 1.21.1 moved panorama rendering to Screen.renderPanorama(GuiGraphics,float), already handled by
// MixinScreen. This adapter is only needed on 1.19.2 and 1.20.1.
//? if >=26 {
//?} elif >=1.20 && <1.21 {

/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.ui.MainMenuBackgroundRenderer;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreenBackground {
    @Inject(method = "render", at = @At("HEAD"))
    private void fpsmaster$renderConfiguredTitleBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) {
            MainMenuBackgroundRenderer.render(graphics, graphics.guiWidth(), graphics.guiHeight(), partialTick);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"))
    private void fpsmaster$skipPanorama(PanoramaRenderer instance, float deltaT, float alpha) {
        if (MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) instance.render(deltaT, alpha);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"))
    private void fpsmaster$skipPanoramaOverlay(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width,
                                                int height, float u, float v, int uw, int vh, int tw, int th) {
        if (MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) {
            graphics.blit(texture, x, y, width, height, u, v, uw, vh, tw, th);
        }
    }
}

*///?} else if <1.20 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.ui.MainMenuBackgroundRenderer;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreenBackground {
    @Inject(method = "render", at = @At("HEAD"))
    private void fpsmaster$renderConfiguredTitleBackground(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) {
            top.fpsmaster.compat.GuiGraphics graphics = new top.fpsmaster.compat.GuiGraphics(poseStack);
            MainMenuBackgroundRenderer.render(graphics, graphics.guiWidth(), graphics.guiHeight(), partialTick);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"))
    private void fpsmaster$skipPanorama(PanoramaRenderer instance, float deltaT, float alpha) {
        if (MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) instance.render(deltaT, alpha);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIFFIIII)V"))
    private void fpsmaster$skipPanoramaOverlay(PoseStack poseStack, int x, int y, int width, int height,
                                                float u, float v, int uw, int vh, int tw, int th) {
        if (MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) {
            GuiComponent.blit(poseStack, x, y, width, height, u, v, uw, vh, tw, th);
        }
    }
}

*///?}
