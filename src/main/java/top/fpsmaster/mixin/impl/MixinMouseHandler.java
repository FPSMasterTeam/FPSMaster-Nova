package top.fpsmaster.mixin.impl;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.optimization.SmoothZoom;
import top.fpsmaster.module.impl.render.FreeLook;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$handleSmoothZoomScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        if (SmoothZoom.handleScroll(yOffset)) {
            ci.cancel();
        }
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void fpsmaster$turnFreeLookCamera(LocalPlayer player, double yawDelta, double pitchDelta) {
        if (!FreeLook.handleTurn(yawDelta, pitchDelta)) {
            player.turn(yawDelta, pitchDelta);
        }
    }
}
