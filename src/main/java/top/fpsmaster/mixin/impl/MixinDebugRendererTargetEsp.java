package top.fpsmaster.mixin.impl;

//? if >=1.21.9 {

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

//?} elif >=1.21.5 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.TargetDisplay;

// 1.21.5..1.21.8 draw debug renderers via DebugRenderer.render(...); it has no partialTick parameter, so
// pull it from the client delta tracker. (Target ESP itself only draws on >=1.21.11 via Gizmos, so this
// hook is effectively a no-op here but keeps the injection valid.)
@Mixin(DebugRenderer.class)
public class MixinDebugRendererTargetEsp {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V",
            at = @At("TAIL")
    )
    private void fpsmaster$emitTargetEsp(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, CallbackInfo ci) {
        TargetDisplay.emitTargetEsp(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}*/

//?} elif >=1.21.1 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.TargetDisplay;

// 1.21.1 is pre-1.21.5 immediate-mode: DebugRenderer.render has no Frustum param and no partialTick
// argument (pull it from the client timer). Draw the ESP box right after the vanilla debug renderers,
// while the line buffer and camera position are valid.
@Mixin(DebugRenderer.class)
public class MixinDebugRendererTargetEsp {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V",
            at = @At("TAIL")
    )
    private void fpsmaster$emitTargetEsp(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, CallbackInfo ci) {
        if (!TargetDisplay.isActive()) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        TargetDisplay.renderTargetEsp1201(poseStack, bufferSource, camX, camY, camZ, partialTick);
    }
}*/

//?} elif >=1.20 {

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
}

*///?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.phys.Vec3;
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

    // Same hook as 1.20.1, but 1.19.2 predates JOML: renderLevel's projection matrix is a
    // com.mojang.math.Matrix4f, so the inject signature must use that type to match the target.
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
}

*///?}
