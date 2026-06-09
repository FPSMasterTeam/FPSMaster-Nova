package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.optimization.Optimization;

@Mixin(FramerateLimitTracker.class)
public class MixinFramerateLimitTracker {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$limitFpsWhenUnfocused(CallbackInfoReturnable<Integer> cir) {
        int fpsLimit = Optimization.fpsLimitWhenUnfocused();
        if (fpsLimit > 0 && !minecraft.isWindowActive()) {
            cir.setReturnValue(fpsLimit);
        }
    }
}
