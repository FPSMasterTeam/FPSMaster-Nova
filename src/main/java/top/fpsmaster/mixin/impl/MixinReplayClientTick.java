package top.fpsmaster.mixin.impl;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.auxiliary.Replay;

/**
 * Drives the parts of a replay that belong on the client thread: sampling the recording player's own
 * position and equipment, which no clientbound packet carries, and advancing playback.
 *
 * Hooked on {@code Minecraft.tick} rather than through the event bus because the bus only dispatches
 * a client tick from 1.21.5 onwards, and a recording has to sample on every version.
 */
@Mixin(Minecraft.class)
public class MixinReplayClientTick {

    @Inject(method = "tick", at = @At("HEAD"))
    private void fpsmaster$replayTick(CallbackInfo ci) {
        try {
            Replay.onClientTick();
        } catch (Throwable failure) {
            // Never take the client tick down with a recording.
        }
    }
}
