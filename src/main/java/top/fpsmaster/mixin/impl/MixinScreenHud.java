package top.fpsmaster.mixin.impl;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.hud.HudEditorScreen;
import top.fpsmaster.hud.HudManager;

/**
 * Keeps the HUD visible while a screen is open (ClickGUI, inventory, …). The normal HUD hook lives in
 * Gui.render, which Minecraft does not call when a screen is up, so without this the HUD vanishes
 * whenever a screen opens. Screen.render takes GuiGraphics on 1.20+ and PoseStack on 1.19.2 (wrapped
 * in the GuiGraphics shim there).
 */
@Mixin(Screen.class)
public class MixinScreenHud {
    //? if >=1.20 {
    @Inject(method = "render", at = @At("TAIL"))
    private void fpsmaster$renderHudOverScreen(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        // The HUD editor renders its own draggable preview; don't double-draw the live HUD over it.
        if ((Object) this instanceof HudEditorScreen) {
            return;
        }
        HudManager.INSTANCE.renderHud(guiGraphics);
    }
    //?} else {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void fpsmaster$renderHudOverScreen(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if ((Object) this instanceof HudEditorScreen) {
            return;
        }
        HudManager.INSTANCE.renderHud(new top.fpsmaster.compat.GuiGraphics(poseStack));
    }*///?}
}
