package top.fpsmaster.mixin.impl;

//? if >=26 {

/*import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At;
import top.fpsmaster.module.impl.render.FireModifier;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {
    @ModifyConstant(method = "lambda$submitFire$0", constant = @Constant(floatValue = -0.3F))
    private static float fpsmaster$moveFireOverlay(float value) {
        return FireModifier.adjustedY(value);
    }

    @ModifyArg(
            method = "buildFireQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"
            ),
            index = 8
    )
    private static int fpsmaster$colorFireOverlay(int color) {
        if (FireModifier.useCustomColor()) {
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.mixin("screen-effect");
                top.fpsmaster.diagnostics.Smoke.feature("fire-modifier");
            }
            return FireModifier.color.argb(0.0f);
        }
        return color;
    }
}
*///?} elif >=1.21.5 {


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

//?} else {

/*import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.render.FireModifier;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {
    // 1.20.1 ScreenEffectRenderer.renderFire translates the overlay by -0.3F on the Y axis.
    @ModifyConstant(method = "renderFire", constant = @Constant(floatValue = -0.3F))
    private static float fpsmaster$moveFireOverlay(float value) {
        return FireModifier.adjustedY(value);
    }

    // VertexConsumer.color was renamed setColor in 1.21; use the compat bridge so this legacy
    // renderer body remains one Stonecutter branch.
    @Redirect(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;color(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer fpsmaster$colorFireOverlay(VertexConsumer vertexConsumer, float red, float green, float blue, float alpha) {
        return top.fpsmaster.compat.VertexColor.set(vertexConsumer,
                FireModifier.useCustomColor() ? FireModifier.red() : red,
                FireModifier.useCustomColor() ? FireModifier.green() : green,
                FireModifier.useCustomColor() ? FireModifier.blue() : blue,
                alpha);
    }
}

*///?}
