package top.fpsmaster.mixin.impl;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.render.FullBright;

@Mixin(LightTexture.class)
public class MixinLightTexture {
    //? if >=1.21.5 {
    @Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
                    ordinal = 2
            )
    )
    private Object fpsmaster$adjustGamma(OptionInstance<Double> optionInstance) {
        return FullBright.adjustGamma(optionInstance.get());
    }
    //?} else {
    /*@Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
                    ordinal = 1
            )
    )
    private Object fpsmaster$adjustGamma(OptionInstance<Double> optionInstance) {
        return FullBright.adjustGamma(optionInstance.get());
    }*///?}
}
