package top.fpsmaster.mixin.impl;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.optimization.NoHitDelay;
import top.fpsmaster.module.impl.render.DamageIndicator;
import top.fpsmaster.module.impl.render.MoreParticles;
import top.fpsmaster.module.impl.ui.ComboDisplay;
import top.fpsmaster.module.impl.ui.ReachDisplay;
import top.fpsmaster.module.impl.ui.TargetDisplay;

@Mixin(Minecraft.class)
public class MixinMinecraftNoHitDelay {
    @Shadow
    public int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void fpsmaster$clearMissTime(CallbackInfoReturnable<Boolean> cir) {
        ComboDisplay.recordAttack((Minecraft) (Object) this);
        DamageIndicator.recordAttack((Minecraft) (Object) this);
        MoreParticles.recordAttack((Minecraft) (Object) this);
        ReachDisplay.recordAttack((Minecraft) (Object) this);
        TargetDisplay.recordAttack((Minecraft) (Object) this);
        if (NoHitDelay.isActive()) {
            this.missTime = 0;
        }
    }
}
