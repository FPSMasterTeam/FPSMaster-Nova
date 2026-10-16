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
 * Hooked on {@code Minecraft.tick} directly rather than through the event bus so that a failing
 * recording is isolated here and never reaches the other TickEvent subscribers.
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
