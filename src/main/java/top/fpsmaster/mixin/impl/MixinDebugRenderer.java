package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.Hitboxes;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void fpsmaster$emitHitboxes(Frustum frustum, double cameraX, double cameraY, double cameraZ, float partialTick, CallbackInfo ci) {
        if (!Hitboxes.isActive() || Minecraft.getInstance().level == null || Minecraft.getInstance().getConnection() == null) {
            return;
        }

        DebugValueAccess debugValueAccess = Minecraft.getInstance().getConnection().createDebugValueAccess();
        new EntityHitboxDebugRenderer(Minecraft.getInstance()).emitGizmos(cameraX, cameraY, cameraZ, debugValueAccess, frustum, partialTick);
    }
}

//?} else {

/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import top.fpsmaster.module.impl.render.Hitboxes;

@Mixin(EntityRenderDispatcher.class)
public class MixinDebugRenderer {
    // Feature 4: force-show hitboxes. EntityRenderDispatcher.render gates the hitbox draw on the
    // private field renderHitBoxes (not the shouldRenderHitBoxes() getter), so override that read.
    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderHitBoxes:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean fpsmaster$forceHitboxes(boolean original) {
        return original || Hitboxes.isActive();
    }

    // Feature 4: recolor. renderHitbox draws the entity bounding box via LevelRenderer.renderLineBox
    // (the AABB overload); recolor its float r,g,b,a arguments using the configured Hitboxes colour.
    @ModifyArgs(
            method = "renderHitbox",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/AABB;FFFF)V")
    )
    private static void fpsmaster$hitboxColor(Args args) {
        if (!Hitboxes.isActive()) {
            return;
        }
        int argb = Hitboxes.colorArgb(0);
        args.set(3, ((argb >> 16) & 0xFF) / 255.0F);
        args.set(4, ((argb >> 8) & 0xFF) / 255.0F);
        args.set(5, (argb & 0xFF) / 255.0F);
        args.set(6, ((argb >> 24) & 0xFF) / 255.0F);
    }
}

*///?}
