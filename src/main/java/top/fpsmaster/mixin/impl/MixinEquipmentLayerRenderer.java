package top.fpsmaster.mixin.impl;

//? if >=1.21.8 {

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.render.Animation;

// renderLayers is overloaded: the short form only forwards to the long one with a null texture
// override, and every OverlayTexture.NO_OVERLAY read lives in the long form. A bare
// method = "renderLayers" selector therefore also matches the forwarding overload, which has no
// field read to redirect, and defaultRequire:1 turns that into a hard injection failure that aborts
// the initial resource reload (black screen). Pin the descriptor to the long overload; the short one
// is covered transitively. The signature changed in 1.21.11 (SubmitNodeCollector/Identifier plus the
// two trailing ints), so it needs its own branch.
@Mixin(EquipmentLayerRenderer.class)
public class MixinEquipmentLayerRenderer {
    @Redirect(
            //? if >=1.21.11 {
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            //?} else {
            /*method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/resources/ResourceLocation;)V",
            *///?}
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I")
    )
    private int fpsmaster$oldDamageOverlay() {
        if (Animation.Companion.getArmorHurtOverlay()) {
            return OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(true));
        }
        return OverlayTexture.NO_OVERLAY;
    }
}

//?} else {

/*import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HumanoidArmorLayer.class)
public class MixinEquipmentLayerRenderer {
}
*///?}
