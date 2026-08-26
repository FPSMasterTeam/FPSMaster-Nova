package top.fpsmaster.mixin.impl;

// EntityHitboxDebugRenderer and the net.minecraft.gizmos API are 1.21.11+. Before that the hitbox draw
// lives in EntityRenderDispatcher, so both halves of the Hitboxes feature (force-show and recolour) are
// carried by MixinDebugRenderer's pre-1.21.11 branches and this compilation unit is empty. The mixin
// config for those versions must therefore not list MixinEntityHitboxDebugRenderer.
//? if >=1.21.11 {

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

//?}
