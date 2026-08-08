package top.fpsmaster.mixin.impl;

//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.gui.Gui;
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.hud.HudManager;
import top.fpsmaster.module.impl.render.Crosshair;
import top.fpsmaster.module.impl.render.HideIndicator;
//? if >=1.21.5 {
import top.fpsmaster.module.impl.ui.CustomTitles;
import top.fpsmaster.module.impl.ui.Scoreboard;
//?}
import top.fpsmaster.notification.NotificationManager;

@Mixin(Gui.class)
public class MixinGui {
    // 26.2 deferred-render: the in-game HUD is now built by Gui.extractRenderState(DeltaTracker, …),
    // which records into the private guiRenderState (no GuiGraphics passed). We shadow that state,
    // build a GuiGraphicsExtractor over it, wrap it in the shim, and draw the FPSMaster HUD at TAIL so
    // it records into the same frame. Vanilla-element hiding (crosshair/effects/scoreboard/title) is
    // deferred on 26.2 (those methods moved to the new Hud class). [[nova-mc26-unobfuscated-build]]
    //? if >=26 {
    /*@org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private net.minecraft.client.renderer.state.gui.GuiRenderState guiRenderState;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fpsmaster$renderHudComponents(DeltaTracker deltaTracker, boolean bl, boolean bl2, CallbackInfo ci) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.GuiGraphicsExtractor extractor = new net.minecraft.client.gui.GuiGraphicsExtractor(
            mc, this.guiRenderState, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        top.fpsmaster.compat.GuiGraphics26 guiGraphics = new top.fpsmaster.compat.GuiGraphics26(extractor);
        Crosshair.render(guiGraphics);
        HudManager.INSTANCE.render(guiGraphics, deltaTracker);
        NotificationManager.render(guiGraphics);
    }
    *///?}
    //? if >=1.21.5 && <26 {
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

    @Inject(method = "render", at = @At("HEAD"))
    private void fpsmaster$renderHudComponents(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Crosshair.render(guiGraphics);
        HudManager.INSTANCE.render(guiGraphics, deltaTracker);
        NotificationManager.render(guiGraphics);
    }
    //?}
    // 1.21..1.21.4: DeltaTracker exists (so the Gui methods carry it) but this is still the pre-1.21.5
    // immediate-mode render era, so CustomTitles/Scoreboard (which need the Matrix3x2fStack pose API) are
    // not ported here — same feature set as the 1.20.1 legacy branch, just with the DeltaTracker param.
    //? if >=1.21 && <1.21.5 {
    /*@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
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

    @Inject(method = "render", at = @At("HEAD"))
    private void fpsmaster$renderHudComponents(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Crosshair.render(guiGraphics);
        HudManager.INSTANCE.render(guiGraphics, deltaTracker);
        NotificationManager.render(guiGraphics);
    }*/
    //?}
    //? if >=1.20 && <1.21 {
    /*@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideVanillaCrosshair(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (Crosshair.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideEffectIndicators(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (HideIndicator.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void fpsmaster$renderHudComponents(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        Crosshair.render(guiGraphics);
        HudManager.INSTANCE.render(guiGraphics, partialTick);
        NotificationManager.render(guiGraphics);
    }*/
    //?}
    //? if <1.20 {
    /*@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideVanillaCrosshair(com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        if (Crosshair.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideEffectIndicators(com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        if (HideIndicator.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void fpsmaster$renderHudComponents(com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick, CallbackInfo ci) {
        top.fpsmaster.compat.GuiGraphics guiGraphics = new top.fpsmaster.compat.GuiGraphics(poseStack);
        Crosshair.render(guiGraphics);
        HudManager.INSTANCE.render(guiGraphics, partialTick);
        NotificationManager.render(guiGraphics);
    }*/
    //?}
}
