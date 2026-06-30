package top.fpsmaster.mixin.impl;

//? if <1.21.5 {

/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.ui.MainMenuBackgroundRenderer;

// 1.20.1-only: the title-screen panorama is rendered inline in TitleScreen.render (there is no
// Screen.renderPanorama like on 1.21.5). Draw the configured custom main-menu background and skip the
// vanilla panorama + overlay when a non-panorama background is selected.
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreenBackground {
    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "render", at = @At("HEAD"))
    private void fpsmaster$renderConfiguredTitleBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) {
            return;
        }
        MainMenuBackgroundRenderer.render(guiGraphics, this.width, this.height, partialTick);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"))
    private void fpsmaster$skipPanorama(PanoramaRenderer instance, float deltaT, float alpha) {
        if (MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) {
            instance.render(deltaT, alpha);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"))
    private void fpsmaster$skipPanoramaOverlay(
            GuiGraphics guiGraphics,
            ResourceLocation atlasLocation,
            int x,
            int y,
            int blitWidth,
            int blitHeight,
            float uOffset,
            float vOffset,
            int uWidth,
            int vHeight,
            int textureWidth,
            int textureHeight
    ) {
        if (MainMenuBackgroundRenderer.shouldUseVanillaPanorama()) {
            guiGraphics.blit(atlasLocation, x, y, blitWidth, blitHeight, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
        }
    }
}*/

//?}
