package top.fpsmaster.mixin.impl;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.optimization.Optimization;

@Mixin(Particle.class)
public class MixinParticle {
    @Inject(method = "getLightColor", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$staticParticleColor(float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (Optimization.shouldUseStaticParticleColor()) {
            cir.setReturnValue(15728880);
        }
    }
}
