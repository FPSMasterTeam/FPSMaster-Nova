package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.optimization.BetterFishingRod;

@Mixin(FishingHookRenderer.class)
public class MixinFishingHookRenderer {
    @Redirect(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/Window;getAppropriateLineWidth()F"
            )
    )
    private float fpsmaster$adjustFishingLineWidth(Window window) {
        return BetterFishingRod.resolveLineWidth(window.getAppropriateLineWidth());
    }
}

//?} else {

/*import net.minecraft.client.renderer.FpsmasterFishingLine;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.optimization.BetterFishingRod;

@Mixin(FishingHookRenderer.class)
public class MixinFishingHookRenderer {
    // 1.20.1 has no Window.getAppropriateLineWidth; the fishing line uses RenderType.lineStrip()
    // whose width comes from an empty LineStateShard. Redirect it to a width-matched LINE_STRIP type
    // built by FpsmasterFishingLine (which lives in the renderer package to reach the protected composite).
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;lineStrip()Lnet/minecraft/client/renderer/RenderType;")
    )
    private RenderType fpsmaster$fishingLineWidth() {
        if (!BetterFishingRod.isActive()) {
            return RenderType.lineStrip();
        }
        return FpsmasterFishingLine.lineStrip(BetterFishingRod.resolveLineWidth(1.0F));
    }
}*/

//?}
