package top.fpsmaster.mixin.impl;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
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
import top.fpsmaster.replay.adapter.DirectorRenderAdapter;
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
        // Director last: while it drives, the keyed FOV is the shot and a held zoom key must not
        // change what gets exported. Below 26 this calculation lives on GameRenderer.getFov, and
        // MixinGameRenderer makes the same pair of calls there.
        cir.setReturnValue(DirectorRenderAdapter.modifyFov(SmoothZoom.modifyFov(cir.getReturnValue())));
        if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
            top.fpsmaster.diagnostics.Smoke.mixin("camera");
            top.fpsmaster.diagnostics.Smoke.feature("smooth-zoom");
        }
    }
    *///? }

    /**
     * Folds the director's roll into the camera's own rotation.
     *
     * Vanilla builds {@code rotation} out of yaw and pitch alone and then derives the basis vectors
     * from it, so appending a rotation about the local Z axis here tilts the view and everything
     * that reads the camera's orientation along with it. {@code forwards} is the axis being rotated
     * about and comes out unchanged, which is why only {@code up} and {@code left} are rebuilt.
     *
     * <p>Patching the camera beats patching the pose stack in the renderer: the place the renderer
     * applies camera rotation moved several times across the six versions this builds for, while
     * {@code setRotation(float, float)} has not moved at all. Below 1.21 the renderer does not read
     * {@code rotation} for the world pass at all, so {@code MixinGameRenderer} rolls the pose stack
     * there as well — this hook is what keeps particles, billboards and sound panning agreeing with
     * the picture on those versions, not what tilts it.
     *
     * <p>The sign and the {@code left} axis are per-version because the camera basis is. From
     * 1.21.1 vanilla's own constants are {@code FORWARDS = (0,0,-1)} and {@code LEFT = (-1,0,0)} and
     * {@code setRotation} carries a {@code PI} yaw offset; before that they are {@code (0,0,1)} and
     * {@code (1,0,0)} with no offset. The two camera-local frames therefore differ by a half turn
     * about Y, and conjugating a Z rotation by that half turn negates it — so the same visible tilt
     * (Edge's, positive counter-clockwise) is {@code -roll} on the newer frame and {@code +roll} on
     * the older one. Rebuilding {@code left} from the wrong axis would flip the camera's idea of
     * left and right, which is what pans sound to the wrong ear.
     */
    //? if >=1.21.1 {
    @Shadow
    @Final
    private org.joml.Quaternionf rotation;
    @Shadow
    @Final
    private org.joml.Vector3f up;
    @Shadow
    @Final
    private org.joml.Vector3f left;

    @Inject(method = "setRotation", at = @At("TAIL"))
    private void fpsmaster$applyDirectorRoll(float yRot, float xRot, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        float roll = DirectorRenderAdapter.getRollDegrees();
        if (roll == 0f) {
            return;
        }
        this.rotation.rotateZ(-roll * ((float) Math.PI / 180f));
        this.up.set(0f, 1f, 0f).rotate(this.rotation);
        this.left.set(-1f, 0f, 0f).rotate(this.rotation);
    }
    //?} else if >=1.20.1 {
    /*@Shadow
    @Final
    private org.joml.Quaternionf rotation;
    @Shadow
    @Final
    private org.joml.Vector3f up;
    @Shadow
    @Final
    private org.joml.Vector3f left;

    @Inject(method = "setRotation", at = @At("TAIL"))
    private void fpsmaster$applyDirectorRoll(float yRot, float xRot, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        float roll = DirectorRenderAdapter.getRollDegrees();
        if (roll == 0f) {
            return;
        }
        this.rotation.rotateZ(roll * ((float) Math.PI / 180f));
        this.up.set(0f, 1f, 0f).rotate(this.rotation);
        this.left.set(1f, 0f, 0f).rotate(this.rotation);
    }
    *///?} else {
    /*@Shadow
    @Final
    private com.mojang.math.Quaternion rotation;
    @Shadow
    @Final
    private com.mojang.math.Vector3f up;
    @Shadow
    @Final
    private com.mojang.math.Vector3f left;

    @Inject(method = "setRotation", at = @At("TAIL"))
    private void fpsmaster$applyDirectorRoll(float yRot, float xRot, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        float roll = DirectorRenderAdapter.getRollDegrees();
        if (roll == 0f) {
            return;
        }
        this.rotation.mul(com.mojang.math.Vector3f.ZP.rotationDegrees(roll));
        this.up.set(0f, 1f, 0f);
        this.up.transform(this.rotation);
        this.left.set(1f, 0f, 0f);
        this.left.transform(this.rotation);
    }
    *///?}
}
