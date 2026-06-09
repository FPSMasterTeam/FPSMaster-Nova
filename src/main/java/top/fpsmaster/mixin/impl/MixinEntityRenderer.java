package top.fpsmaster.mixin.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
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
}
