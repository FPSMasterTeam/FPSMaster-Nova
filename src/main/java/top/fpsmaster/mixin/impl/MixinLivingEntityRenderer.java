package top.fpsmaster.mixin.impl;

//? if >=1.21.11 {

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.mixin.interfaces.ILivingEntityRenderState;
import top.fpsmaster.module.impl.auxiliary.LevelTag;
import top.fpsmaster.module.impl.render.DamageIndicator;
import top.fpsmaster.module.impl.render.HitColor;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fpsmaster$captureLivingEntityId(LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        ((ILivingEntityRenderState) renderState).fpsmaster$setEntityId(entity.getId());
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfNameTag(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.shouldShowSelfNameTag()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                cir.setReturnValue(!entity.isInvisibleTo(player));
            }
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void fpsmaster$submitDamageIndicators(LivingEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (!DamageIndicator.isActive()) {
            return;
        }

        int entityId = ((ILivingEntityRenderState) renderState).fpsmaster$getEntityId();
        for (DamageIndicator.DisplayEntry entry : DamageIndicator.displayEntriesFor(entityId)) {
            nodeCollector.submitNameTag(
                    poseStack,
                    new Vec3(0.0, renderState.boundingBoxHeight + 0.35F + entry.getYOffset(), 0.0),
                    0,
                    Component.literal(entry.getText()).withColor(entry.getColor() & 0x00FFFFFF),
                    true,
                    renderState.lightCoords,
                    renderState.distanceToCameraSq,
                    cameraRenderState
            );
        }
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$replaceHitTint(LivingEntityRenderState renderState, CallbackInfoReturnable<Integer> cir) {
        if (HitColor.isActive() && renderState.hasRedOverlay) {
            cir.setReturnValue(HitColor.colorArgb());
        }
    }

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void fpsmaster$removeVanillaRedOverlay(LivingEntityRenderState renderState, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        if (HitColor.isActive() && renderState.hasRedOverlay) {
            cir.setReturnValue(OverlayTexture.NO_OVERLAY);
        }
    }
}

//?} else if >=1.21.5 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.mixin.interfaces.ILivingEntityRenderState;
import top.fpsmaster.module.impl.auxiliary.LevelTag;
import top.fpsmaster.module.impl.render.DamageIndicator;
import top.fpsmaster.module.impl.render.HitColor;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fpsmaster$captureLivingEntityId(LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        ((ILivingEntityRenderState) renderState).fpsmaster$setEntityId(entity.getId());
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfNameTag(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.shouldShowSelfNameTag()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                cir.setReturnValue(!entity.isInvisibleTo(player));
            }
        }
    }

    // 1.21.5..1.21.8 are render-state based but have no submit-node collector: the draw entry point is
    // render(state, PoseStack, MultiBufferSource, packedLight) and there is no submitNameTag, so the
    // damage numbers are drawn with the vanilla name-tag technique (cameraOrientation + scale +
    // Font.drawInBatch). packedLight is a parameter here, not a render-state field.
    @Inject(method = "render", at = @At("RETURN"))
    private void fpsmaster$renderDamageIndicators(LivingEntityRenderState renderState, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!DamageIndicator.isActive()) {
            return;
        }

        int entityId = ((ILivingEntityRenderState) renderState).fpsmaster$getEntityId();
        List<DamageIndicator.DisplayEntry> entries = DamageIndicator.displayEntriesFor(entityId);
        if (entries.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        double baseY = renderState.boundingBoxHeight + 0.35F;
        for (DamageIndicator.DisplayEntry entry : entries) {
            poseStack.pushPose();
            poseStack.translate(0.0, baseY + entry.getYOffset(), 0.0);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = poseStack.last().pose();
            String text = entry.getText();
            float x = -font.width(text) / 2.0F;
            font.drawInBatch(text, x, 0.0F, entry.getColor(), false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            poseStack.popPose();
        }
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$replaceHitTint(LivingEntityRenderState renderState, CallbackInfoReturnable<Integer> cir) {
        if (HitColor.isActive() && renderState.hasRedOverlay) {
            cir.setReturnValue(HitColor.colorArgb());
        }
    }

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void fpsmaster$removeVanillaRedOverlay(LivingEntityRenderState renderState, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        if (HitColor.isActive() && renderState.hasRedOverlay) {
            cir.setReturnValue(OverlayTexture.NO_OVERLAY);
        }
    }
}

*///?} else if >=1.21 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.auxiliary.LevelTag;
import top.fpsmaster.module.impl.render.DamageIndicator;
import top.fpsmaster.module.impl.render.HitColor;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    // Feature 1: LevelTag self nametag. LivingEntityRenderer.shouldShowName(LivingEntity) returns boolean.
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.shouldShowSelfNameTag()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                cir.setReturnValue(!entity.isInvisibleTo(player));
            }
        }
    }

    // Feature 2: DamageIndicator. render(...) is void; draw camera-facing text the same way as
    // EntityRenderer.renderNameTag (cameraOrientation + scale + Font.drawInBatch).
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void fpsmaster$renderDamageIndicators(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!DamageIndicator.isActive()) {
            return;
        }

        List<DamageIndicator.DisplayEntry> entries = DamageIndicator.displayEntriesFor(entity.getId());
        if (entries.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        double baseY = entity.getBbHeight() + 0.35;
        for (DamageIndicator.DisplayEntry entry : entries) {
            poseStack.pushPose();
            poseStack.translate(0.0, baseY + entry.getYOffset(), 0.0);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = poseStack.last().pose();
            String text = entry.getText();
            float x = -font.width(text) / 2.0F;
            font.drawInBatch(text, x, 0.0F, entry.getColor(), false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            poseStack.popPose();
        }
    }

    // Feature 3: HitColor. 1.21.x collapsed the model tint to a single packed-ARGB argument, so the
    // redirect signature is renderToBuffer(PoseStack, VertexConsumer, int light, int overlay, int color).
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void fpsmaster$hitColor(EntityModel model, PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color, LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (HitColor.isActive() && (entity.hurtTime > 0 || entity.deathTime > 0)) {
            model.renderToBuffer(matrices, vertices, light, OverlayTexture.NO_OVERLAY, HitColor.colorArgb());
        } else {
            model.renderToBuffer(matrices, vertices, light, overlay, color);
        }
    }
}

*///?} else if >=1.20 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.auxiliary.LevelTag;
import top.fpsmaster.module.impl.render.DamageIndicator;
import top.fpsmaster.module.impl.render.HitColor;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    // Feature 1: LevelTag self nametag. LivingEntityRenderer.shouldShowName(LivingEntity) returns boolean.
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.shouldShowSelfNameTag()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                cir.setReturnValue(!entity.isInvisibleTo(player));
            }
        }
    }

    // Feature 2: DamageIndicator. render(...) is void; draw camera-facing text the same way as
    // EntityRenderer.renderNameTag (cameraOrientation + scale + Font.drawInBatch).
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void fpsmaster$renderDamageIndicators(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!DamageIndicator.isActive()) {
            return;
        }

        List<DamageIndicator.DisplayEntry> entries = DamageIndicator.displayEntriesFor(entity.getId());
        if (entries.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        double baseY = entity.getBbHeight() + 0.35;
        for (DamageIndicator.DisplayEntry entry : entries) {
            poseStack.pushPose();
            poseStack.translate(0.0, baseY + entry.getYOffset(), 0.0);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = poseStack.last().pose();
            String text = entry.getText();
            float x = -font.width(text) / 2.0F;
            font.drawInBatch(text, x, 0.0F, entry.getColor(), false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            poseStack.popPose();
        }
    }

    // Feature 3: HitColor. On 1.20.1 the hurt flash is the white overlay (packedOverlay) and the model
    // colour multiplier is passed straight to EntityModel.renderToBuffer. Replace both: drop the vanilla
    // overlay and tint with the configured colour while the entity is hurt/dying.
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void fpsmaster$hitColor(EntityModel model, PoseStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (HitColor.isActive() && (entity.hurtTime > 0 || entity.deathTime > 0)) {
            int argb = HitColor.colorArgb();
            float na = ((argb >> 24) & 0xFF) / 255.0F;
            float nr = ((argb >> 16) & 0xFF) / 255.0F;
            float ng = ((argb >> 8) & 0xFF) / 255.0F;
            float nb = (argb & 0xFF) / 255.0F;
            model.renderToBuffer(matrices, vertices, light, OverlayTexture.NO_OVERLAY, nr, ng, nb, na);
        } else {
            model.renderToBuffer(matrices, vertices, light, overlay, red, green, blue, alpha);
        }
    }
}

*///?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.auxiliary.LevelTag;
import top.fpsmaster.module.impl.render.DamageIndicator;
import top.fpsmaster.module.impl.render.HitColor;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    // Feature 1: LevelTag self nametag. LivingEntityRenderer.shouldShowName(LivingEntity) returns boolean.
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.shouldShowSelfNameTag()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                cir.setReturnValue(!entity.isInvisibleTo(player));
            }
        }
    }

    // Feature 2: DamageIndicator. Same technique as 1.20.1, but 1.19.2 predates JOML and
    // Font.DisplayMode: PoseStack.Pose.pose() is a com.mojang.math.Matrix4f and drawInBatch takes a
    // boolean seeThrough flag.
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void fpsmaster$renderDamageIndicators(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!DamageIndicator.isActive()) {
            return;
        }

        List<DamageIndicator.DisplayEntry> entries = DamageIndicator.displayEntriesFor(entity.getId());
        if (entries.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        double baseY = entity.getBbHeight() + 0.35;
        for (DamageIndicator.DisplayEntry entry : entries) {
            poseStack.pushPose();
            poseStack.translate(0.0, baseY + entry.getYOffset(), 0.0);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = poseStack.last().pose();
            String text = entry.getText();
            float x = -font.width(text) / 2.0F;
            font.drawInBatch(text, x, 0.0F, entry.getColor(), false, matrix, buffer, true, 0, packedLight);
            poseStack.popPose();
        }
    }

    // Feature 3: HitColor. 1.19.2 has the same float r,g,b,a model tint as 1.20.1.
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void fpsmaster$hitColor(EntityModel model, PoseStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (HitColor.isActive() && (entity.hurtTime > 0 || entity.deathTime > 0)) {
            int argb = HitColor.colorArgb();
            float na = ((argb >> 24) & 0xFF) / 255.0F;
            float nr = ((argb >> 16) & 0xFF) / 255.0F;
            float ng = ((argb >> 8) & 0xFF) / 255.0F;
            float nb = (argb & 0xFF) / 255.0F;
            model.renderToBuffer(matrices, vertices, light, OverlayTexture.NO_OVERLAY, nr, ng, nb, na);
        } else {
            model.renderToBuffer(matrices, vertices, light, overlay, red, green, blue, alpha);
        }
    }
}

*///?}
