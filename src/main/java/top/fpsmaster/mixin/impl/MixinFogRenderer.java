package top.fpsmaster.mixin.impl;

//? if >=1.21.8 {

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.render.CustomFog;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @Unique
    private static Camera fpsmaster$lastCamera;

    //? if >=1.21.11 {
    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$fogColor(
            Camera camera,
            float partialTick,
            ClientLevel level,
            int renderDistance,
            float darkenWorldAmount,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        fpsmaster$lastCamera = camera;
        if (!fpsmaster$shouldOverrideColor(camera)) {
            return;
        }
        cir.setReturnValue(new Vector4f(
                CustomFog.redFraction(),
                CustomFog.greenFraction(),
                CustomFog.blueFraction(),
                1.0F
        ));
    }
    //?} else {
    /*@Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$fogColor(
            Camera camera,
            float partialTick,
            ClientLevel level,
            int renderDistance,
            float darkenWorldAmount,
            boolean unused,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        fpsmaster$lastCamera = camera;
        if (!fpsmaster$shouldOverrideColor(camera)) {
            return;
        }
        cir.setReturnValue(new Vector4f(
                CustomFog.redFraction(),
                CustomFog.greenFraction(),
                CustomFog.blueFraction(),
                1.0F
        ));
    }
    *///?}

    // 26.2 added a public updateBuffer(FogData) alongside the private buffer-writing one, and that
    // overload has no float parameters for these ordinals to bind to. The private overload's descriptor
    // is identical on 1.21.8, 1.21.11 and 26.2, so pinning it needs no version branch.
    @ModifyVariable(method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float fpsmaster$fogStart(float environmentalStart) {
        return fpsmaster$shouldOverrideDistance() ? CustomFog.startDistance(environmentalStart) : environmentalStart;
    }

    @ModifyVariable(method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float fpsmaster$fogEnd(float environmentalEnd) {
        return fpsmaster$shouldOverrideDistance() ? CustomFog.endDistance(environmentalEnd) : environmentalEnd;
    }

    @ModifyVariable(method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private float fpsmaster$skyEnd(float skyEnd) {
        return CustomFog.overridesSky() && fpsmaster$shouldOverrideDistance()
                ? CustomFog.endDistance(skyEnd)
                : skyEnd;
    }

    @Unique
    private static boolean fpsmaster$shouldOverrideColor(Camera camera) {
        FogType type = camera.getFluidInCamera();
        return CustomFog.appliesTo(type == FogType.WATER, type == FogType.LAVA)
                && CustomFog.overridesColor();
    }

    @Unique
    private static boolean fpsmaster$shouldOverrideDistance() {
        Camera camera = fpsmaster$lastCamera;
        if (camera == null) {
            return false;
        }
        FogType type = camera.getFluidInCamera();
        return CustomFog.appliesTo(type == FogType.WATER, type == FogType.LAVA)
                && CustomFog.overridesDistance();
    }
}

//?} else {

/*import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.CustomFog;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void fpsmaster$fogColor(
            Camera camera,
            float partialTicks,
            ClientLevel level,
            int renderDistanceChunks,
            float bossColorModifier,
            CallbackInfo ci
    ) {
        FogType type = camera.getFluidInCamera();
        if (!CustomFog.appliesTo(type == FogType.WATER, type == FogType.LAVA) || !CustomFog.overridesColor()) {
            return;
        }
        fogRed = CustomFog.redFraction();
        fogGreen = CustomFog.greenFraction();
        fogBlue = CustomFog.blueFraction();
    }
}
*///?}
