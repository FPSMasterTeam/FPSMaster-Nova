package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.render.HideIndicator;

// canSeeEffects only asks whether there is room to the left to draw the effect panel (it returns
// getGuiLeft() >= 32 and writes nothing). Forcing it false hides the effect icons in the inventory; it
// does not move the inventory. The screen's leftPos is only ever written by AbstractContainerScreen
// (centering) and the recipe book's updateScreenPosition, on every version Nova builds for.
@Mixin(EffectsInInventory.class)
public class MixinEffectsInInventory {
    @Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideInventoryEffects(CallbackInfoReturnable<Boolean> cir) {
        if (HideIndicator.isActive()) {
            cir.setReturnValue(false);
        }
    }
}


//?} else {

/*import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.render.HideIndicator;

// 1.20.1: no "EffectsInInventory" helper; the effect overlay decision is EffectRenderingInventoryScreen.canSeeEffects() (boolean).
// Same as the branch above: read-only, so this hides the icons rather than repositioning anything.
@Mixin(EffectRenderingInventoryScreen.class)
public class MixinEffectsInInventory {
    @Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hideInventoryEffects(CallbackInfoReturnable<Boolean> cir) {
        if (HideIndicator.isActive()) {
            cir.setReturnValue(false);
        }
    }
}

*///?}
