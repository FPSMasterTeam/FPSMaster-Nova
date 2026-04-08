package top.fpsmaster.mixin.impl;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.optimization.NoHitDelay;

@Mixin(Minecraft.class)
public class MixinMinecraftNoHitDelay {
    @Shadow
    public int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void fpsmaster$clearMissTime(CallbackInfoReturnable<Boolean> cir) {
        if (NoHitDelay.isActive()) {
            this.missTime = 0;
        }
    }
}
