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

//?}
