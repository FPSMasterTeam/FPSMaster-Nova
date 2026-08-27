package top.fpsmaster.mixin.impl;

//? if >=26 {

/*import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.FullBright;

@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightTexture {
    @Inject(method = "extract", at = @At("TAIL"))
    private void fpsmaster$fullBright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (FullBright.adjustGamma(0.0) <= 0.0) {
            return;
        }
        if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
            top.fpsmaster.diagnostics.Smoke.mixin("light-texture");
            top.fpsmaster.diagnostics.Smoke.feature("full-bright");
        }
        state.brightness = 1.0F;
        state.blockFactor = 1.0F;
        state.skyFactor = 1.0F;
        state.darknessEffectScale = 0.0F;
        state.blockLightTint = new Vector3f(1.0F, 1.0F, 1.0F);
        state.skyLightColor = new Vector3f(1.0F, 1.0F, 1.0F);
        state.ambientColor = new Vector3f(1.0F, 1.0F, 1.0F);
    }
}
*///?} else {

import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.render.FullBright;

@Mixin(LightTexture.class)
public class MixinLightTexture {
    //? if >=1.21.9 {
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
    }
    *///?}
}
//?}
