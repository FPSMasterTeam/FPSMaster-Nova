package top.fpsmaster.mixin.impl;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.render.Animation;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;abs(F)F"))
    public float forceSideMove(float v) {
        return Animation.isActive() && Animation.Companion.getOldBackward().getValue() ? 0 : v;
    }
}
