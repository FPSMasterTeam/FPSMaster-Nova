package top.fpsmaster.mixin.impl;

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
