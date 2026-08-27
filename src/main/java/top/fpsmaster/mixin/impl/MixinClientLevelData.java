package top.fpsmaster.mixin.impl;

//? if >=26 {

/*import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.auxiliary.TimeChanger;

@Mixin(Level.class)
public class MixinClientLevelData {
    @Inject(method = "getOverworldClockTime", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$overrideDayTime(CallbackInfoReturnable<Long> cir) {
        if (TimeChanger.Companion.getWorking()) {
            cir.setReturnValue(TimeChanger.Companion.getTime().getValue().longValue());
        }
    }
}
*///?} else {

import net.minecraft.client.multiplayer.ClientLevel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.auxiliary.TimeChanger;

@Mixin(ClientLevel.ClientLevelData.class)
public class MixinClientLevelData {
    @Shadow
    private long dayTime;

    @Redirect(method = "getDayTime", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/ClientLevel$ClientLevelData;dayTime:J", opcode = Opcodes.GETFIELD))
    public long getGameTime(ClientLevel.ClientLevelData data) {
        if (TimeChanger.Companion.getWorking()) {
            return TimeChanger.Companion.getTime().getValue().longValue();
        }
        return dayTime;
    }
}
//?}
