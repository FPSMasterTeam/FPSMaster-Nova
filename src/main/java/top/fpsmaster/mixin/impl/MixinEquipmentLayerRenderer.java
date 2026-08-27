package top.fpsmaster.mixin.impl;

//? if >=1.21.8 {

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EquipmentLayerRenderer.class)
public class MixinEquipmentLayerRenderer {
    @Redirect(
            method = "renderLayers",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I")
    )
    private int fpsmaster$oldDamageOverlay() {
        if (MixinHumanoidArmorLayer.fpsmaster$hurt) {
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
