package top.fpsmaster.mixin.impl;

//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.web.cef.ImeSupport;

/**
 * Optional in-game IME positioning for vanilla text fields (chat, anvil rename, server address,
 * search boxes, …). While a vanilla EditBox is focused, anchor the OS IME candidate window at the
 * field instead of letting it fall back to a screen corner. No-op on builds without the GLFW preedit
 * API (1.20.1 / LWJGL 3.3.2) — see ImeSupport / docs/ime-support.md.
 *
 * AbstractWidget.renderWidget(GuiGraphics,int,int,float) is stable across 1.20.1/1.21.x, so a single
 * mixin works on both. On 1.19.2 the method is renderButton(PoseStack,int,int,float) and the widget
 * bounds are the public AbstractWidget.x/y fields (getX()/getY() arrived in 1.20). X is the field's
 * left edge plus the standard text inset; a precise per-caret x would need the field's internal
 * cursor/scroll state (future refinement).
 */
@Mixin(EditBox.class)
public class MixinEditBox {
    //? if >=26 {
    /*@Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void fpsmaster$positionIme(net.minecraft.client.gui.GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        EditBox self = (EditBox) (Object) this;
        if (!self.isFocused() || !self.isVisible()) {
            return;
        }
        ImeSupport.INSTANCE.setEnabled(true);
        ImeSupport.INSTANCE.positionAtGui(self.getX() + 4.0, self.getY() + (double) self.getHeight(), self.getHeight());
    }
    *///?} else if >=1.20 {
    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void fpsmaster$positionIme(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        EditBox self = (EditBox) (Object) this;
        if (!self.isFocused() || !self.isVisible()) {
            return;
        }
        ImeSupport.INSTANCE.setEnabled(true);
        ImeSupport.INSTANCE.positionAtGui(self.getX() + 4.0, self.getY() + (double) self.getHeight(), self.getHeight());
    }
    //?} else {
    /*@Inject(method = "renderButton", at = @At("TAIL"))
    private void fpsmaster$positionIme(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        EditBox self = (EditBox) (Object) this;
        if (!self.isFocused() || !self.isVisible()) {
            return;
        }
        ImeSupport.INSTANCE.setEnabled(true);
        // Left edge + text inset (~4px), anchored at the bottom of the field so the candidate drops below.
        ImeSupport.INSTANCE.positionAtGui(self.x + 4.0, self.y + (double) self.getHeight(), self.getHeight());
    }*/
    //?}
}
