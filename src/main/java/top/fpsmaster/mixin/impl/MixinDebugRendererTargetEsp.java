package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.TargetDisplay;

@Mixin(DebugRenderer.class)
public class MixinDebugRendererTargetEsp {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void fpsmaster$emitTargetEsp(Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfo ci) {
        TargetDisplay.emitTargetEsp(partialTick);
    }
}

//?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.TargetDisplay;

@Mixin(LevelRenderer.class)
public class MixinDebugRendererTargetEsp {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    // Feature 5: draw the target ESP box right after the vanilla debug renderer, while the line buffer
    // and camera position are valid. renderLevel is void, so this is a CallbackInfo inject.
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V",
                    shift = At.Shift.AFTER
            )
    )
    private void fpsmaster$renderTargetEsp(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!TargetDisplay.isActive()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        TargetDisplay.renderTargetEsp1201(poseStack, this.renderBuffers.bufferSource(), cam.x, cam.y, cam.z, partialTick);
    }
}*/

//?}
