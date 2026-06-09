package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.render.FireModifier;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {
    @ModifyConstant(method = "renderFire", constant = @Constant(floatValue = -0.3F))
    private static float fpsmaster$moveFireOverlay(float value) {
        return FireModifier.adjustedY(value);
    }

    @Redirect(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer fpsmaster$colorFireOverlay(VertexConsumer vertexConsumer, float red, float green, float blue, float alpha) {
        if (FireModifier.useCustomColor()) {
            return vertexConsumer.setColor(FireModifier.red(), FireModifier.green(), FireModifier.blue(), alpha);
        }
        return vertexConsumer.setColor(red, green, blue, alpha);
    }
}
