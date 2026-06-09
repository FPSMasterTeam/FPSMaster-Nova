package top.fpsmaster.mixin.impl;

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
