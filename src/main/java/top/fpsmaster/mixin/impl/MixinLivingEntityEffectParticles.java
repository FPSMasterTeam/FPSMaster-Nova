package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.CleanView;

@Mixin(LivingEntity.class)
public class MixinLivingEntityEffectParticles {
    @Inject(method = "updateSynchronizedMobEffectParticles", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideLocalEffectParticles(CallbackInfo ci) {
        if (CleanView.shouldHideEffectParticles((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}

//?} else {

/*import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.render.CleanView;

@Mixin(LivingEntity.class)
public class MixinLivingEntityEffectParticles {
    // 1.20.1 has no updateSynchronizedMobEffectParticles: the effect particles are spawned by an
    // addParticle call inside tickEffects(). Redirect that call (scoped to tickEffects) and suppress
    // it for entities CleanView wants hidden, leaving the rest of the effect logic intact.
    @Redirect(method = "tickEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void fpsmaster$hideLocalEffectParticles(Level level, ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
        if (CleanView.shouldHideEffectParticles((LivingEntity) (Object) this)) {
            return;
        }
        level.addParticle(options, x, y, z, vx, vy, vz);
    }
}*/

//?}
