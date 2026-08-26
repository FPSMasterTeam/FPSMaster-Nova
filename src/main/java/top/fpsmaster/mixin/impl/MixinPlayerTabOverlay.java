package top.fpsmaster.mixin.impl;

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.auxiliary.NameProtect;
import top.fpsmaster.module.impl.ui.TabOverlay;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {
    @Shadow
    @Final
    private Minecraft minecraft;

    // 1.19.2 renderPingIcon takes a PoseStack instead of a GuiGraphics; the rest of the draw is
    // identical, so it runs through the compat GuiGraphics shim.
    //? if >=1.20 {
    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hidePingIcon(GuiGraphics guiGraphics, int width, int x, int y, PlayerInfo playerInfo, CallbackInfo ci) {
        if (TabOverlay.shouldOverridePing()) {
            if (TabOverlay.shouldShowPingText()) {
                int latency = playerInfo.getLatency();
                String text = latency + "ms";
                int color = latency < 150 ? 0xFF55FF55 : latency < 300 ? 0xFFFFFF55 : 0xFFFF5555;
                int textX = x + width - minecraft.font.width(text);
                guiGraphics.drawString(minecraft.font, text, textX, y, color);
            }
            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hidePingIcon(com.mojang.blaze3d.vertex.PoseStack poseStack, int width, int x, int y, PlayerInfo playerInfo, CallbackInfo ci) {
        if (TabOverlay.shouldOverridePing()) {
            if (TabOverlay.shouldShowPingText()) {
                int latency = playerInfo.getLatency();
                String text = latency + "ms";
                int color = latency < 150 ? 0xFF55FF55 : latency < 300 ? 0xFFFFFF55 : 0xFFFF5555;
                int textX = x + width - minecraft.font.width(text);
                new top.fpsmaster.compat.GuiGraphics(poseStack).drawString(minecraft.font, text, textX, y, color);
            }
            ci.cancel();
        }
    }*/
    //?}

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void fpsmaster$protectPlayerName(CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        String filtered = NameProtect.filter(original.getString());
        if (filtered.equals(original.getString())) {
            return;
        }

        cir.setReturnValue(Component.literal(filtered).withStyle(original.getStyle()));
    }
}
