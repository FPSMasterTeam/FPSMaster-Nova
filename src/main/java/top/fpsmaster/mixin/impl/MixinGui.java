package top.fpsmaster.mixin.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.hud.HudManager;
import top.fpsmaster.module.impl.render.Crosshair;
import top.fpsmaster.module.impl.render.HideIndicator;
import top.fpsmaster.module.impl.ui.CustomTitles;
import top.fpsmaster.module.impl.ui.Scoreboard;
import top.fpsmaster.notification.NotificationManager;

@Mixin(Gui.class)
public class MixinGui {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideVanillaCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Crosshair.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideEffectIndicators(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (HideIndicator.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideScoreboard(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Scoreboard.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTitle", at = @At("HEAD"))
    private void fpsmaster$transformTitle(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CustomTitles.isActive()) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(CustomTitles.xOffset(), CustomTitles.yOffset());
            guiGraphics.pose().scale(CustomTitles.scaleValue(), CustomTitles.scaleValue());
        }
    }

    @Inject(method = "renderTitle", at = @At("TAIL"))
    private void fpsmaster$restoreTitleTransform(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CustomTitles.isActive()) {
            guiGraphics.pose().popMatrix();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fpsmaster$renderHudComponents(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Crosshair.render(guiGraphics);
        HudManager.INSTANCE.render(guiGraphics, deltaTracker);
        NotificationManager.render(guiGraphics);
    }
}
