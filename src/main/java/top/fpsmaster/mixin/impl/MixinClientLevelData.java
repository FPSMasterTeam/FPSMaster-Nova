package top.fpsmaster.mixin.impl;

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
