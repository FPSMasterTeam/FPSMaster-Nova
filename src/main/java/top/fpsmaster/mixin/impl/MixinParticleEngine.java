package top.fpsmaster.mixin.impl;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.auxiliary.ParticlesModifier;
import top.fpsmaster.module.impl.optimization.Optimization;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideBlockParticles(Particle particle, CallbackInfo ci) {
        if (Optimization.shouldCullParticle() || (ParticlesModifier.shouldHideBlockParticles() && particle instanceof TerrainParticle)) {
            ci.cancel();
        }
    }
}
