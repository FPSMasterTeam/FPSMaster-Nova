package top.fpsmaster.mixin.impl;

// 26.2 moved crosshair/effects/scoreboard/title off Gui onto Hud.extract*.
//? if >=26 {

/*import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.compat.GuiGraphics26;
import top.fpsmaster.module.impl.render.Crosshair;
import top.fpsmaster.module.impl.render.HideIndicator;
import top.fpsmaster.module.impl.ui.CustomTitles;
import top.fpsmaster.module.impl.ui.Scoreboard;

@Mixin(Hud.class)
public class MixinHud {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideVanillaCrosshair(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Crosshair.isActive()) {
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.mixin("hud");
                top.fpsmaster.diagnostics.Smoke.feature("crosshair");
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideEffectIndicators(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (HideIndicator.isActive()) {
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.mixin("hud");
                top.fpsmaster.diagnostics.Smoke.feature("hide-indicator");
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideScoreboard(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Scoreboard.isActive()) {
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.mixin("hud");
                top.fpsmaster.diagnostics.Smoke.feature("scoreboard");
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractTitle", at = @At("HEAD"))
    private void fpsmaster$transformTitle(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CustomTitles.isActive()) {
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.mixin("hud");
                top.fpsmaster.diagnostics.Smoke.feature("custom-titles");
            }
            GuiGraphics26 graphics = new GuiGraphics26(extractor);
            graphics.pose().pushMatrix();
            graphics.pose().translate(CustomTitles.xOffset(), CustomTitles.yOffset());
            graphics.pose().scale(CustomTitles.scaleValue(), CustomTitles.scaleValue());
        }
    }

    @Inject(method = "extractTitle", at = @At("TAIL"))
    private void fpsmaster$restoreTitleTransform(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CustomTitles.isActive()) {
            new GuiGraphics26(extractor).pose().popMatrix();
        }
    }
}
*/
//?}
