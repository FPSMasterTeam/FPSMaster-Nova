package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
//? if <26 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.auxiliary.LevelTag;
import top.fpsmaster.module.impl.optimization.Optimization;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer<T extends Entity> {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    public void shouldRender(T livingEntity, Frustum camera, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (Optimization.shouldIgnoreArmorStand() && livingEntity instanceof ArmorStand) {
            cir.setReturnValue(false);
            cir.cancel();
        } else if (Optimization.shouldCullByEntityLimit() && livingEntity != Minecraft.getInstance().player) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    //? if >=1.21.5 {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfName(T entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.isActive() && LevelTag.Companion.getShowSelf().getValue() && entity == Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$appendHealthToNameTag(T entity, CallbackInfoReturnable<Component> cir) {
        if (!LevelTag.isActive() || !LevelTag.Companion.getHealth().getValue() || !(entity instanceof Player) || !(entity instanceof LivingEntity)) {
            return;
        }

        String name = cir.getReturnValue().getString();
        if (name.contains("[NPC]")) {
            return;
        }

        LivingEntity livingEntity = (LivingEntity) entity;
        cir.setReturnValue(Component.literal(name + " " + Math.round(livingEntity.getHealth()) + " hp"));
    }
    //?} else if >=1.21 && <1.21.5 {
    /*@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfName(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.isActive() && LevelTag.Companion.getShowSelf().getValue() && entity == Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }

    // 1.21 added a tickDelta argument to renderNameTag; keep the 1.20.1 five-arg redirect below.
    @Redirect(
            method = "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V")
    )
    private void fpsmaster$appendHealthToNameTag(EntityRenderer<T> instance, T entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float tickDelta) {
        Component name = displayName;
        if (LevelTag.isActive() && LevelTag.Companion.getHealth().getValue() && entity instanceof Player && entity instanceof LivingEntity
                && !displayName.getString().contains("[NPC]")) {
            name = Component.literal(displayName.getString() + " " + Math.round(((LivingEntity) entity).getHealth()) + " hp");
        }
        instance.renderNameTag(entity, name, poseStack, buffer, packedLight, tickDelta);
    }
    *///?} else if <1.21 {
    /*@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$showSelfName(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (LevelTag.isActive() && LevelTag.Companion.getShowSelf().getValue() && entity == Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }

    // 1.20.1 has no EntityRenderer.getNameTag; append health by redirecting the renderNameTag call.
    @Redirect(
            method = "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    )
    private void fpsmaster$appendHealthToNameTag(EntityRenderer<T> instance, T entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Component name = displayName;
        if (LevelTag.isActive() && LevelTag.Companion.getHealth().getValue() && entity instanceof Player && entity instanceof LivingEntity
                && !displayName.getString().contains("[NPC]")) {
            name = Component.literal(displayName.getString() + " " + Math.round(((LivingEntity) entity).getHealth()) + " hp");
        }
        instance.renderNameTag(entity, name, poseStack, buffer, packedLight);
    }
    *///?}
}
