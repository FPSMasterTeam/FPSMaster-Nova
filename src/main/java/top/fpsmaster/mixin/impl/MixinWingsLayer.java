package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.DragonWings;

@Mixin(WingsLayer.class)
public class MixinWingsLayer {
    @Unique
    private static final Identifier DRAGON_WINGS_TEXTURE = Identifier.withDefaultNamespace("client/wings/wings.png");
    @Unique
    private static final float UNIT = 1.0F / 16.0F;

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpsmaster$submitDragonWings(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, HumanoidRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        if (!this.fpsmaster$shouldRenderDragonWings(state)) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, state.isCrouching ? 0.125F : 0.0F, 0.2F);
        float scale = DragonWings.scaleMultiplier();
        poseStack.scale(scale, scale, scale);
        nodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutoutNoCull(DRAGON_WINGS_TEXTURE),
                (pose, vertexConsumer) -> this.fpsmaster$renderDragonWings(pose, vertexConsumer, packedLight)
        );
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private void fpsmaster$renderDragonWings(PoseStack.Pose pose, VertexConsumer vertexConsumer, int packedLight) {
        float animation = (System.currentTimeMillis() % 1000L) / 1000.0F * (float) Math.PI * 2.0F;
        int color = DragonWings.renderColor();

        for (int side = 0; side < 2; side++) {
            float mirror = side == 0 ? 1.0F : -1.0F;
            Matrix4f wingMatrix = new Matrix4f()
                    .translate(-2.0F * UNIT * mirror, 0.0F, 0.0F)
                    .rotateZ((float) Math.toRadians(20.0F) * mirror)
                    .rotateY((float) Math.toRadians(20.0F) * mirror + (float) Math.sin(animation) * 0.4F * mirror)
                    .rotateX((float) Math.toRadians(-80.0F) - (float) Math.cos(animation) * 0.2F);

            Matrix4f tipMatrix = new Matrix4f(wingMatrix)
                    .translate(-10.0F * UNIT * mirror, 0.0F, 0.0F)
                    .rotateZ(-((float) Math.sin(animation + 2.0F) + 0.5F) * 0.75F * mirror);

            this.fpsmaster$renderBone(vertexConsumer, pose, wingMatrix, -10.0F * UNIT * mirror, -1.0F * UNIT, -1.0F * UNIT, 10.0F * UNIT * mirror, 2.0F * UNIT, 2.0F * UNIT, 0.0F, 0.0F, 10.0F / 30.0F, 2.0F / 30.0F, color, packedLight);
            this.fpsmaster$renderMembrane(vertexConsumer, pose, wingMatrix, -10.0F * UNIT * mirror, 0.0F, 0.5F * UNIT, 10.0F * UNIT * mirror, 10.0F * UNIT, 0.0F, 0.0F, 8.0F / 30.0F, 10.0F / 30.0F, 18.0F / 30.0F, color, packedLight);
            this.fpsmaster$renderBone(vertexConsumer, pose, tipMatrix, -10.0F * UNIT * mirror, -0.5F * UNIT, -0.5F * UNIT, 10.0F * UNIT * mirror, UNIT, UNIT, 0.0F, 5.0F / 30.0F, 10.0F / 30.0F, 6.0F / 30.0F, color, packedLight);
            this.fpsmaster$renderMembrane(vertexConsumer, pose, tipMatrix, -10.0F * UNIT * mirror, 0.0F, 0.5F * UNIT, 10.0F * UNIT * mirror, 10.0F * UNIT, 0.0F, 0.0F, 18.0F / 30.0F, 10.0F / 30.0F, 28.0F / 30.0F, color, packedLight);
        }
    }

    @Unique
    private void fpsmaster$renderBone(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x, float y, float z, float width, float height, float depth, float u0, float v0, float u1, float v1, int color, int packedLight) {
        float x2 = x + width;
        float y2 = y + height;
        float z2 = z + depth;
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z2, x2, y, z2, x2, y2, z2, x, y2, z2, u0, v0, u1, v1, 0.0F, 0.0F, 1.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x2, y, z, x, y, z, x, y2, z, x2, y2, z, u0, v0, u1, v1, 0.0F, 0.0F, -1.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z, x, y, z2, x, y2, z2, x, y2, z, u0, v0, u1, v1, -1.0F, 0.0F, 0.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x2, y, z2, x2, y, z, x2, y2, z, x2, y2, z2, u0, v0, u1, v1, 1.0F, 0.0F, 0.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z, x2, y, z, x2, y, z2, x, y, z2, u0, v0, u1, v1, 0.0F, -1.0F, 0.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x, y2, z2, x2, y2, z2, x2, y2, z, x, y2, z, u0, v0, u1, v1, 0.0F, 1.0F, 0.0F, color, packedLight);
    }

    @Unique
    private void fpsmaster$renderMembrane(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x, float y, float z, float width, float length, float depth, float u0, float v0, float u1, float v1, int color, int packedLight) {
        float x2 = x + width;
        float z2 = z + length * UNIT / UNIT + depth;
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z, x2, y, z, x2, y, z2, x, y, z2, u0, v0, u1, v1, 0.0F, -1.0F, 0.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z2, x2, y, z2, x2, y, z, x, y, z, u0, v1, u1, v0, 0.0F, 1.0F, 0.0F, color, packedLight);
    }

    @Unique
    private void fpsmaster$quad(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u0, float v0, float u1, float v1, float normalX, float normalY, float normalZ, int color, int packedLight) {
        this.fpsmaster$vertex(buffer, pose, matrix, x1, y1, z1, u0, v0, normalX, normalY, normalZ, color, packedLight);
        this.fpsmaster$vertex(buffer, pose, matrix, x2, y2, z2, u1, v0, normalX, normalY, normalZ, color, packedLight);
        this.fpsmaster$vertex(buffer, pose, matrix, x3, y3, z3, u1, v1, normalX, normalY, normalZ, color, packedLight);
        this.fpsmaster$vertex(buffer, pose, matrix, x4, y4, z4, u0, v1, normalX, normalY, normalZ, color, packedLight);
    }

    @Unique
    private void fpsmaster$vertex(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ, int color, int packedLight) {
        Vector4f position = new Vector4f(x, y, z, 1.0F);
        matrix.transform(position);
        buffer.addVertex(pose, position.x(), position.y(), position.z())
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    @Unique
    private boolean fpsmaster$shouldRenderDragonWings(HumanoidRenderState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DragonWings.isActive() || minecraft.player == null || minecraft.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (!(state instanceof AvatarRenderState avatarRenderState)) {
            return false;
        }
        return avatarRenderState.id == minecraft.player.getId() && !avatarRenderState.isInvisible;
    }
}

//?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.DragonWings;

@Mixin(ElytraLayer.class)
public class MixinWingsLayer {
    @Unique
    private static final ResourceLocation DRAGON_WINGS_TEXTURE = new ResourceLocation("client/wings/wings.png");
    @Unique
    private static final float UNIT = 1.0F / 16.0F;

    // 1.20.1: the elytra layer is ElytraLayer and renders directly (no render-state / SubmitNodeCollector).
    // Inject at render() HEAD, draw the custom dragon-wing geometry to a VertexConsumer, cancel vanilla.
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpsmaster$submitDragonWings(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!this.fpsmaster$shouldRenderDragonWings(entity)) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, entity.isCrouching() ? 0.125F : 0.0F, 0.2F);
        float scale = DragonWings.scaleMultiplier();
        poseStack.scale(scale, scale, scale);
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(DRAGON_WINGS_TEXTURE, false));
        this.fpsmaster$renderDragonWings(poseStack.last(), vertexConsumer, packedLight);
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private void fpsmaster$renderDragonWings(PoseStack.Pose pose, VertexConsumer vertexConsumer, int packedLight) {
        float animation = (System.currentTimeMillis() % 1000L) / 1000.0F * (float) Math.PI * 2.0F;
        int color = DragonWings.renderColor();

        for (int side = 0; side < 2; side++) {
            float mirror = side == 0 ? 1.0F : -1.0F;
            Matrix4f wingMatrix = new Matrix4f()
                    .translate(-2.0F * UNIT * mirror, 0.0F, 0.0F)
                    .rotateZ((float) Math.toRadians(20.0F) * mirror)
                    .rotateY((float) Math.toRadians(20.0F) * mirror + (float) Math.sin(animation) * 0.4F * mirror)
                    .rotateX((float) Math.toRadians(-80.0F) - (float) Math.cos(animation) * 0.2F);

            Matrix4f tipMatrix = new Matrix4f(wingMatrix)
                    .translate(-10.0F * UNIT * mirror, 0.0F, 0.0F)
                    .rotateZ(-((float) Math.sin(animation + 2.0F) + 0.5F) * 0.75F * mirror);

            this.fpsmaster$renderBone(vertexConsumer, pose, wingMatrix, -10.0F * UNIT * mirror, -1.0F * UNIT, -1.0F * UNIT, 10.0F * UNIT * mirror, 2.0F * UNIT, 2.0F * UNIT, 0.0F, 0.0F, 10.0F / 30.0F, 2.0F / 30.0F, color, packedLight);
            this.fpsmaster$renderMembrane(vertexConsumer, pose, wingMatrix, -10.0F * UNIT * mirror, 0.0F, 0.5F * UNIT, 10.0F * UNIT * mirror, 10.0F * UNIT, 0.0F, 0.0F, 8.0F / 30.0F, 10.0F / 30.0F, 18.0F / 30.0F, color, packedLight);
            this.fpsmaster$renderBone(vertexConsumer, pose, tipMatrix, -10.0F * UNIT * mirror, -0.5F * UNIT, -0.5F * UNIT, 10.0F * UNIT * mirror, UNIT, UNIT, 0.0F, 5.0F / 30.0F, 10.0F / 30.0F, 6.0F / 30.0F, color, packedLight);
            this.fpsmaster$renderMembrane(vertexConsumer, pose, tipMatrix, -10.0F * UNIT * mirror, 0.0F, 0.5F * UNIT, 10.0F * UNIT * mirror, 10.0F * UNIT, 0.0F, 0.0F, 18.0F / 30.0F, 10.0F / 30.0F, 28.0F / 30.0F, color, packedLight);
        }
    }

    @Unique
    private void fpsmaster$renderBone(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x, float y, float z, float width, float height, float depth, float u0, float v0, float u1, float v1, int color, int packedLight) {
        float x2 = x + width;
        float y2 = y + height;
        float z2 = z + depth;
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z2, x2, y, z2, x2, y2, z2, x, y2, z2, u0, v0, u1, v1, 0.0F, 0.0F, 1.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x2, y, z, x, y, z, x, y2, z, x2, y2, z, u0, v0, u1, v1, 0.0F, 0.0F, -1.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z, x, y, z2, x, y2, z2, x, y2, z, u0, v0, u1, v1, -1.0F, 0.0F, 0.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x2, y, z2, x2, y, z, x2, y2, z, x2, y2, z2, u0, v0, u1, v1, 1.0F, 0.0F, 0.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z, x2, y, z, x2, y, z2, x, y, z2, u0, v0, u1, v1, 0.0F, -1.0F, 0.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x, y2, z2, x2, y2, z2, x2, y2, z, x, y2, z, u0, v0, u1, v1, 0.0F, 1.0F, 0.0F, color, packedLight);
    }

    @Unique
    private void fpsmaster$renderMembrane(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x, float y, float z, float width, float length, float depth, float u0, float v0, float u1, float v1, int color, int packedLight) {
        float x2 = x + width;
        float z2 = z + length * UNIT / UNIT + depth;
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z, x2, y, z, x2, y, z2, x, y, z2, u0, v0, u1, v1, 0.0F, -1.0F, 0.0F, color, packedLight);
        this.fpsmaster$quad(buffer, pose, matrix, x, y, z2, x2, y, z2, x2, y, z, x, y, z, u0, v1, u1, v0, 0.0F, 1.0F, 0.0F, color, packedLight);
    }

    @Unique
    private void fpsmaster$quad(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u0, float v0, float u1, float v1, float normalX, float normalY, float normalZ, int color, int packedLight) {
        this.fpsmaster$vertex(buffer, pose, matrix, x1, y1, z1, u0, v0, normalX, normalY, normalZ, color, packedLight);
        this.fpsmaster$vertex(buffer, pose, matrix, x2, y2, z2, u1, v0, normalX, normalY, normalZ, color, packedLight);
        this.fpsmaster$vertex(buffer, pose, matrix, x3, y3, z3, u1, v1, normalX, normalY, normalZ, color, packedLight);
        this.fpsmaster$vertex(buffer, pose, matrix, x4, y4, z4, u0, v1, normalX, normalY, normalZ, color, packedLight);
    }

    @Unique
    private void fpsmaster$vertex(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ, int color, int packedLight) {
        Vector4f position = new Vector4f(x, y, z, 1.0F);
        matrix.transform(position);
        buffer.vertex(pose.pose(), position.x(), position.y(), position.z())
                .color(color)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
    }

    @Unique
    private boolean fpsmaster$shouldRenderDragonWings(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DragonWings.isActive() || minecraft.player == null || minecraft.options.getCameraType().isFirstPerson()) {
            return false;
        }
        return entity.getId() == minecraft.player.getId() && !entity.isInvisible();
    }
}*/

//?}
