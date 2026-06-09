package top.fpsmaster.mixin.impl;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.render.FreeLook;

@Mixin(Camera.class)
public class MixinCamera {
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float fpsmaster$freeLookYaw(Entity entity, float partialTick) {
        return FreeLook.cameraYaw(entity.getViewYRot(partialTick));
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float fpsmaster$freeLookPitch(Entity entity, float partialTick) {
        return FreeLook.cameraPitch(entity.getViewXRot(partialTick));
    }
}
