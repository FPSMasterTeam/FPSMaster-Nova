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

//?} elif >=1.20 {

/*import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.fpsmaster.module.impl.auxiliary.LevelTag;

@Mixin(EntityRenderer.class)
public class MixinNameTagFeatureRenderer {
    // On 1.20.1/1.21.1 the nametag background colour is the alpha-only int passed as Font.drawInBatch's
    // backgroundColor argument (index 8) inside EntityRenderer.renderNameTag. The second drawInBatch
    // call passes 0, which LevelTag.nameTagBackgroundColor returns unchanged, so matching both is safe.
    @ModifyArg(
            method = "renderNameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
            ),
            index = 8
    )
    private int fpsmaster$replaceNameTagBackgroundColor(int backgroundColor) {
        return LevelTag.nameTagBackgroundColor(backgroundColor);
    }
}*/

//?} else {

/*import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.fpsmaster.module.impl.auxiliary.LevelTag;

@Mixin(EntityRenderer.class)
public class MixinNameTagFeatureRenderer {
    // 1.19.2 predates the JOML migration and Font.DisplayMode: Font.drawInBatch takes a
    // com.mojang.math.Matrix4f and a boolean (seeThrough) where later versions pass a Font$DisplayMode.
    // backgroundColor is still index 8.
    @ModifyArg(
            method = "renderNameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;ZII)I"
            ),
            index = 8
    )
    private int fpsmaster$replaceNameTagBackgroundColor(int backgroundColor) {
        return LevelTag.nameTagBackgroundColor(backgroundColor);
    }
}*/

//?}
