package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.WavyCape;

@Mixin(CapeLayer.class)
public class MixinCapeLayer {
    @Unique
    private static final int SEGMENTS = 18;
    @Unique
    private static final float CAPE_WIDTH = 0.6F;
    @Unique
    private static final float CAPE_LENGTH = 1.08F;
    @Unique
    private static final float CAPE_DEPTH = 0.06F;

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpsmaster$submitWavyCape(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, AvatarRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        if (!WavyCape.isActive() || state.isInvisible || !state.showCape || state.skin.cape() == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, state.isCrouching ? 0.04F : 0.0F, 0.175F);
        nodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entitySolid(state.skin.cape().texturePath()),
                (pose, vertexConsumer) -> this.fpsmaster$renderWavyCape(pose, vertexConsumer, packedLight, state)
        );
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private void fpsmaster$renderWavyCape(PoseStack.Pose pose, VertexConsumer vertexConsumer, int packedLight, AvatarRenderState state) {
        Matrix4f previousMatrix = null;
        float segmentLength = CAPE_LENGTH / SEGMENTS;

        for (int segment = 0; segment < SEGMENTS; segment++) {
            Matrix4f currentMatrix = this.fpsmaster$segmentMatrix(state, segment);
            float top = (segment - 1) * segmentLength;
            float bottom = segment * segmentLength;

            if (previousMatrix != null) {
                this.fpsmaster$renderCapeSegment(vertexConsumer, pose, previousMatrix, currentMatrix, top, bottom, segment, packedLight);
            }
            if (segment == 0) {
                this.fpsmaster$renderTopSegment(vertexConsumer, pose, currentMatrix, packedLight);
            }
            previousMatrix = currentMatrix;
        }
    }

    @Unique
    private Matrix4f fpsmaster$segmentMatrix(AvatarRenderState state, int segment) {
        float progress = (segment + 1.0F) / SEGMENTS;
        float eased = (float) Math.sin(progress * Math.PI / 2.0);
        float wind = (float) Math.sin((System.currentTimeMillis() / 3.0 + segment * 20.0) * Math.PI / 180.0) * 3.0F;
        float walk = Mth.clamp(state.walkAnimationSpeed * 10.0F, 0.0F, 8.0F) * eased;
        float flap = Mth.clamp(6.0F + state.capeFlap * 0.35F + walk + wind, -6.0F, 42.0F);
        float lean = Mth.clamp(state.capeLean * 0.18F, -12.0F, 12.0F);
        float side = Mth.clamp(state.capeLean2 * 0.45F + wind * 0.35F, -20.0F, 20.0F);

        return new Matrix4f()
                .rotateX((float) Math.toRadians(flap * eased))
                .rotateZ((float) Math.toRadians(side * eased))
                .rotateY((float) Math.toRadians(180.0F - lean * eased));
    }

    @Unique
    private void fpsmaster$renderCapeSegment(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f previousMatrix, Matrix4f currentMatrix, float top, float bottom, int segment, int packedLight) {
        this.fpsmaster$renderBackFace(buffer, pose, previousMatrix, currentMatrix, top, bottom, segment, packedLight);
        this.fpsmaster$renderFrontFace(buffer, pose, previousMatrix, currentMatrix, top, bottom, segment, packedLight);
        this.fpsmaster$renderLeftSide(buffer, pose, previousMatrix, currentMatrix, top, bottom, segment, packedLight);
        this.fpsmaster$renderRightSide(buffer, pose, previousMatrix, currentMatrix, top, bottom, segment, packedLight);

        if (segment == SEGMENTS - 1) {
            this.fpsmaster$renderBottomEdge(buffer, pose, previousMatrix, currentMatrix, top, bottom, packedLight);
        }
    }

    @Unique
    private void fpsmaster$renderBackFace(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f previousMatrix, Matrix4f currentMatrix, float top, float bottom, int segment, int packedLight) {
        float[] v = this.fpsmaster$getVerticalTexCoords(segment, 0.03125F, 0.53125F);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, -CAPE_WIDTH / 2, top, -CAPE_DEPTH, 0.015625F, v[0], 0.0F, 0.0F, -1.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, CAPE_WIDTH / 2, top, -CAPE_DEPTH, 0.171875F, v[0], 0.0F, 0.0F, -1.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, CAPE_WIDTH / 2, bottom, -CAPE_DEPTH, 0.171875F, v[1], 0.0F, 0.0F, -1.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, -CAPE_WIDTH / 2, bottom, -CAPE_DEPTH, 0.015625F, v[1], 0.0F, 0.0F, -1.0F, packedLight);
    }

    @Unique
    private void fpsmaster$renderFrontFace(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f previousMatrix, Matrix4f currentMatrix, float top, float bottom, int segment, int packedLight) {
        float[] v = this.fpsmaster$getVerticalTexCoords(segment, 0.03125F, 0.53125F);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, -CAPE_WIDTH / 2, bottom, 0.0F, 0.1875F, v[1], 0.0F, 0.0F, 1.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, CAPE_WIDTH / 2, bottom, 0.0F, 0.34375F, v[1], 0.0F, 0.0F, 1.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, CAPE_WIDTH / 2, top, 0.0F, 0.34375F, v[0], 0.0F, 0.0F, 1.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, -CAPE_WIDTH / 2, top, 0.0F, 0.1875F, v[0], 0.0F, 0.0F, 1.0F, packedLight);
    }

    @Unique
    private void fpsmaster$renderLeftSide(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f previousMatrix, Matrix4f currentMatrix, float top, float bottom, int segment, int packedLight) {
        float[] v = this.fpsmaster$getVerticalTexCoords(segment, 0.03125F, 0.53125F);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, -CAPE_WIDTH / 2, top, 0.0F, 0.015625F, v[0], -1.0F, 0.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, -CAPE_WIDTH / 2, top, -CAPE_DEPTH, 0.0F, v[0], -1.0F, 0.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, -CAPE_WIDTH / 2, bottom, -CAPE_DEPTH, 0.0F, v[1], -1.0F, 0.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, -CAPE_WIDTH / 2, bottom, 0.0F, 0.015625F, v[1], -1.0F, 0.0F, 0.0F, packedLight);
    }

    @Unique
    private void fpsmaster$renderRightSide(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f previousMatrix, Matrix4f currentMatrix, float top, float bottom, int segment, int packedLight) {
        float[] v = this.fpsmaster$getVerticalTexCoords(segment, 0.03125F, 0.53125F);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, CAPE_WIDTH / 2, top, -CAPE_DEPTH, 0.171875F, v[0], 1.0F, 0.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, CAPE_WIDTH / 2, top, 0.0F, 0.1875F, v[0], 1.0F, 0.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, CAPE_WIDTH / 2, bottom, 0.0F, 0.1875F, v[1], 1.0F, 0.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, CAPE_WIDTH / 2, bottom, -CAPE_DEPTH, 0.171875F, v[1], 1.0F, 0.0F, 0.0F, packedLight);
    }

    @Unique
    private void fpsmaster$renderTopSegment(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, int packedLight) {
        this.fpsmaster$addVertex(buffer, pose, matrix, -CAPE_WIDTH / 2, 0.0F, 0.0F, 0.015625F, 0.03125F, 0.0F, 1.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, matrix, CAPE_WIDTH / 2, 0.0F, 0.0F, 0.171875F, 0.03125F, 0.0F, 1.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, matrix, CAPE_WIDTH / 2, 0.0F, -CAPE_DEPTH, 0.171875F, 0.0F, 0.0F, 1.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, matrix, -CAPE_WIDTH / 2, 0.0F, -CAPE_DEPTH, 0.015625F, 0.0F, 0.0F, 1.0F, 0.0F, packedLight);
    }

    @Unique
    private void fpsmaster$renderBottomEdge(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f previousMatrix, Matrix4f currentMatrix, float top, float bottom, int packedLight) {
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, -CAPE_WIDTH / 2, bottom, -CAPE_DEPTH, 0.171875F, 0.0F, 0.0F, -1.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, previousMatrix, CAPE_WIDTH / 2, bottom, -CAPE_DEPTH, 0.328125F, 0.0F, 0.0F, -1.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, CAPE_WIDTH / 2, top, 0.0F, 0.328125F, 0.03125F, 0.0F, -1.0F, 0.0F, packedLight);
        this.fpsmaster$addVertex(buffer, pose, currentMatrix, -CAPE_WIDTH / 2, top, 0.0F, 0.171875F, 0.03125F, 0.0F, -1.0F, 0.0F, packedLight);
    }

    @Unique
    private float[] fpsmaster$getVerticalTexCoords(int segment, float min, float max) {
        float step = (max - min) / SEGMENTS;
        return new float[]{min + segment * step, min + (segment + 1) * step};
    }

    @Unique
    private void fpsmaster$addVertex(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ, int packedLight) {
        Vector4f position = new Vector4f(x, y, z, 1.0F);
        matrix.transform(position);
        buffer.addVertex(pose, position.x(), position.y(), position.z())
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
