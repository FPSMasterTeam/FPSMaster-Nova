package top.fpsmaster.mixin.impl;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
//? if >= 26 {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.optimization.SmoothZoom;
*///? }
import top.fpsmaster.module.impl.render.Animation;
import top.fpsmaster.module.impl.render.FreeLook;
@Mixin(Camera.class)
public class MixinCamera {
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;
    @Shadow
    private Entity entity;

    @Inject(method = "tick", at = @At("TAIL"))
    private void fpsmaster$snapSneakEye(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!Animation.isActive() || Animation.Companion.getAnimationSneak().getValue()) {
            return;
        }
        if (this.entity == null) {
            return;
        }
        float height = this.entity.getEyeHeight();
        this.eyeHeight = height;
        this.eyeHeightOld = height;
    }
    //? if <= 1.21.11 {
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float fpsmaster$freeLookYaw(Entity entity, float partialTick) {
        return FreeLook.cameraYaw(entity.getViewYRot(partialTick));
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float fpsmaster$freeLookPitch(Entity entity, float partialTick) {
        return FreeLook.cameraPitch(entity.getViewXRot(partialTick));
    }
    //? } else {
    /*@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float fpsmaster$freeLookYaw(Entity entity, float partialTick) {
        return FreeLook.cameraYaw(entity.getViewYRot(partialTick));
    }

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float fpsmaster$freeLookPitch(Entity entity, float partialTick) {
        return FreeLook.cameraPitch(entity.getViewXRot(partialTick));
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    public void fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(SmoothZoom.modifyFov(cir.getReturnValue()));
        if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
            top.fpsmaster.diagnostics.Smoke.mixin("camera");
            top.fpsmaster.diagnostics.Smoke.feature("smooth-zoom");
        }
    }
    *///? }
}
