package top.fpsmaster.mixin.impl;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.auxiliary.SoundModifier;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {
    @Redirect(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
    private float fpsmaster$modifyVolume(SoundInstance instance) {
        return SoundModifier.adjustVolume(instance);
    }
}
