package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.optimization.NoHurtCam;

@Mixin(GameRenderer.class)
public class MixinGameRendererNoHurtCam {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$cancelHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (NoHurtCam.isActive()) {
            ci.cancel();
        }
    }
}
