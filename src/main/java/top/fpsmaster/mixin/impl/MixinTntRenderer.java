package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.auxiliary.TNTTimer;

import java.util.Locale;

@Mixin(TntRenderer.class)
public class MixinTntRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fpsmaster$addTntTimerNameTag(PrimedTnt tnt, TntRenderState renderState, float partialTick, CallbackInfo ci) {
        if (!TNTTimer.isActive()) {
            return;
        }

        double seconds = tnt.getFuse() / 20.0 + TNTTimer.Companion.getDuration().getValue() - 4.0;
        int color = seconds < 1.0 ? 0xFF3333 : seconds < 2.5 ? 0xFFFF33 : 0xFFFFFF;
        renderState.nameTag = Component.literal(String.format(Locale.US, "%.2f", seconds)).withColor(color);
        renderState.nameTagAttachment = tnt.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, tnt.getYRot(partialTick));
    }
}

//?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.auxiliary.TNTTimer;

import java.util.Locale;

@Mixin(TntRenderer.class)
public class MixinTntRenderer {
    // 1.20.1 has no render-state nameTag field: draw the fuse countdown as camera-facing text above
    // the TNT in render() (void -> CallbackInfo), using the vanilla name-tag technique.
    @Inject(method = "render(Lnet/minecraft/world/entity/item/PrimedTnt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void fpsmaster$renderTntTimer(PrimedTnt entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!TNTTimer.isActive()) {
            return;
        }
        double seconds = entity.getFuse() / 20.0 + TNTTimer.Companion.getDuration().getValue() - 4.0;
        int color = 0xFF000000 | (seconds < 1.0 ? 0xFF3333 : seconds < 2.5 ? 0xFFFF33 : 0xFFFFFF);
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Component text = Component.literal(String.format(Locale.US, "%.2f", seconds));
        poseStack.pushPose();
        poseStack.translate(0.0, 1.0, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, 0.0F, color, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, packedLight);
        poseStack.popPose();
    }
}*/

//?}
