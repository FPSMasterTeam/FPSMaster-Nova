package top.fpsmaster.mixin.impl;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import top.fpsmaster.module.impl.optimization.Optimization;

@Mixin(ClientLevel.class)
public class MixinClientLevel {
    @ModifyConstant(method = "animateTick", constant = @Constant(intValue = 667))
    private int fpsmaster$lowerAnimationTickCount(int original) {
        return Optimization.shouldUseLowAnimationTick() ? 67 : original;
    }
}
