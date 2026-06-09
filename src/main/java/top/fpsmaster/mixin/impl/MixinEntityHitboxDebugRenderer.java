package top.fpsmaster.mixin.impl;

import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.fpsmaster.module.impl.render.Hitboxes;

@Mixin(EntityHitboxDebugRenderer.class)
public class MixinEntityHitboxDebugRenderer {
    @ModifyArg(
            method = "showHitboxes",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/GizmoStyle;stroke(I)Lnet/minecraft/gizmos/GizmoStyle;"),
            index = 0
    )
    private int fpsmaster$hitboxColor(int color) {
        return Hitboxes.colorArgb(color);
    }
}
