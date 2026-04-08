package top.fpsmaster.mixin.impl;

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
