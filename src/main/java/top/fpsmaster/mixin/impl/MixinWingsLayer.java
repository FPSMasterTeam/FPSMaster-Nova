package top.fpsmaster.mixin.impl;

//? if >=1.21.11 {

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.cosmetic.CosmeticView;
import top.fpsmaster.cosmetic.DragonWingsRenderer;

@Mixin(WingsLayer.class)
public class MixinWingsLayer {
    @Unique
    private static final Identifier DRAGON_WINGS_TEXTURE = Identifier.withDefaultNamespace("client/wings/wings.png");

    @Inject(method = "getPlayerElytraTexture", at = @At("HEAD"), cancellable = true)
    private static void fpsmaster$customElytraTexture(HumanoidRenderState state, CallbackInfoReturnable<Identifier> cir) {
        if (!(state instanceof AvatarRenderState avatar) || !CosmeticView.rendersElytra(avatar.id)) return;
        Identifier selected = CosmeticView.wingTexture(avatar.id);
        if (selected != null) cir.setReturnValue(selected);
    }

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpsmaster$submitDragonWings(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, HumanoidRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar) || !this.fpsmaster$shouldRenderDragonWings(avatar)) return;
        if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
            top.fpsmaster.diagnostics.Smoke.mixin("wings-layer");
            top.fpsmaster.diagnostics.Smoke.feature("cape-wings-elytra");
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, state.isCrouching ? 0.125F : 0.0F, 0.2F);
        float scale = CosmeticView.wingScale(avatar.id);
        poseStack.scale(scale, scale, scale);
        nodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutoutNoCull(this.fpsmaster$dragonWingsTexture(avatar.id)),
                (pose, vertexConsumer) -> DragonWingsRenderer.render(pose, vertexConsumer, packedLight)
        );
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private boolean fpsmaster$shouldRenderDragonWings(AvatarRenderState avatar) {
        return CosmeticView.rendersDragonWings(avatar.id) &&
                !CosmeticView.hidesBackPiece(avatar.id) && !avatar.isInvisible;
    }

    @Unique
    private Identifier fpsmaster$dragonWingsTexture(int entityId) {
        Identifier selected = CosmeticView.wingTexture(entityId);
        return selected == null ? DRAGON_WINGS_TEXTURE : selected;
    }
}

//?} else if >=1.21.5 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import top.fpsmaster.cosmetic.CosmeticView;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.cosmetic.DragonWingsRenderer;

@Mixin(WingsLayer.class)
public class MixinWingsLayer {
    @Unique
    private static final ResourceLocation DRAGON_WINGS_TEXTURE = ResourceLocation.withDefaultNamespace("client/wings/wings.png");

    @Inject(method = "getPlayerElytraTexture", at = @At("HEAD"), cancellable = true)
    private static void fpsmaster$customElytraTexture(HumanoidRenderState state, CallbackInfoReturnable<ResourceLocation> cir) {
        if (!(state instanceof PlayerRenderState playerState) || !CosmeticView.rendersElytra(playerState.id)) return;
        ResourceLocation selected = CosmeticView.wingTexture(playerState.id);
        if (selected != null) cir.setReturnValue(selected);
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpsmaster$renderDragonWings(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HumanoidRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        if (!(state instanceof PlayerRenderState playerState) ||
                !this.fpsmaster$shouldRenderDragonWings(playerState)) return;
        poseStack.pushPose();
        poseStack.translate(0.0F, state.isCrouching ? 0.125F : 0.0F, 0.2F);
        float scale = CosmeticView.wingScale(playerState.id);
        poseStack.scale(scale, scale, scale);
        VertexConsumer vertexConsumer = buffer.getBuffer(
                RenderType.entityCutoutNoCull(this.fpsmaster$dragonWingsTexture(playerState.id), false));
        DragonWingsRenderer.render(poseStack.last(), vertexConsumer, packedLight);
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private boolean fpsmaster$shouldRenderDragonWings(PlayerRenderState state) {
        return CosmeticView.rendersDragonWings(state.id) &&
                !CosmeticView.hidesBackPiece(state.id) && !state.isInvisible;
    }

    @Unique
    private ResourceLocation fpsmaster$dragonWingsTexture(int entityId) {
        ResourceLocation selected = CosmeticView.wingTexture(entityId);
        return selected == null ? DRAGON_WINGS_TEXTURE : selected;
    }
}

*///?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import top.fpsmaster.cosmetic.CosmeticView;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.cosmetic.DragonWingsRenderer;

@Mixin(ElytraLayer.class)
public class MixinWingsLayer {
    @Unique
    private static final ResourceLocation DRAGON_WINGS_TEXTURE = ResourceLocation.tryParse("minecraft:client/wings/wings.png");

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpsmaster$renderDragonWings(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!this.fpsmaster$shouldRenderDragonWings(entity)) return;
        poseStack.pushPose();
        poseStack.translate(0.0F, entity.isCrouching() ? 0.125F : 0.0F, 0.2F);
        float scale = CosmeticView.wingScale(entity.getId());
        poseStack.scale(scale, scale, scale);
        VertexConsumer vertexConsumer = buffer.getBuffer(
                RenderType.entityCutoutNoCull(this.fpsmaster$dragonWingsTexture(entity.getId()), false));
        DragonWingsRenderer.render(poseStack.last(), vertexConsumer, packedLight);
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private boolean fpsmaster$shouldRenderDragonWings(LivingEntity entity) {
        return CosmeticView.rendersDragonWings(entity.getId()) &&
                !CosmeticView.hidesBackPiece(entity.getId()) && !entity.isInvisible();
    }

    @Unique
    private ResourceLocation fpsmaster$dragonWingsTexture(int entityId) {
        ResourceLocation selected = CosmeticView.wingTexture(entityId);
        return selected == null ? DRAGON_WINGS_TEXTURE : selected;
    }
}

*///?}
