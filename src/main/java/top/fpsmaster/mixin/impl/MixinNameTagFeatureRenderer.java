package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.fpsmaster.module.impl.auxiliary.LevelTag;

@Mixin(NameTagFeatureRenderer.Storage.class)
public class MixinNameTagFeatureRenderer {
    @ModifyArg(
            method = "add",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4f;FFLnet/minecraft/network/chat/Component;IIID)V"
            ),
            index = 6
    )
    private int fpsmaster$replaceNameTagBackgroundColor(int backgroundColor) {
        return LevelTag.nameTagBackgroundColor(backgroundColor);
    }
}

//?}
