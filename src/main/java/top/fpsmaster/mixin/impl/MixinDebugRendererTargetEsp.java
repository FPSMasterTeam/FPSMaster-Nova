package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.TargetDisplay;

@Mixin(DebugRenderer.class)
public class MixinDebugRendererTargetEsp {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void fpsmaster$emitTargetEsp(Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfo ci) {
        TargetDisplay.emitTargetEsp(partialTick);
    }
}

//?}
