package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.event.client.KeyEvent;
import top.fpsmaster.mixin.interfaces.IKeyMapping;

@Mixin(KeyMapping.class)
public class MixinKeyMapping implements IKeyMapping {
    @Shadow
    protected InputConstants.Key key;

    @Inject(at = @At("HEAD"), method = "click")
    private static void click(InputConstants.Key key, CallbackInfo ci) {
        StandaloneEventAPI.getApi().call(new KeyEvent(key));
    }

    @Override
    public InputConstants.Key getKey() {
        return key;
    }
}
