package top.fpsmaster.mixin.impl;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >= 26 {
/*import org.spongepowered.asm.mixin.injection.Inject;
*///? }
import org.spongepowered.asm.mixin.injection.Redirect;
//? if >= 26 {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.optimization.SmoothZoom;
*///? }
import top.fpsmaster.module.impl.render.FreeLook;

@Mixin(Camera.class)
public class MixinCamera {
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
    /*@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    public void fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(SmoothZoom.modifyFov(cir.getReturnValue()));
    }
    *///? }
}
