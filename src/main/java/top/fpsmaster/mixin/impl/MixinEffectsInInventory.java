package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.optimization.FixedInventory;
import top.fpsmaster.module.impl.render.HideIndicator;

@Mixin(EffectsInInventory.class)
public class MixinEffectsInInventory {
    @Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$keepInventoryCentered(CallbackInfoReturnable<Boolean> cir) {
        if (FixedInventory.isActive() || HideIndicator.isActive()) {
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
import top.fpsmaster.module.impl.optimization.FixedInventory;
import top.fpsmaster.module.impl.render.HideIndicator;

// 1.20.1: no "EffectsInInventory" helper; the effect overlay decision is EffectRenderingInventoryScreen.canSeeEffects() (boolean).
@Mixin(EffectRenderingInventoryScreen.class)
public class MixinEffectsInInventory {
    @Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$keepInventoryCentered(CallbackInfoReturnable<Boolean> cir) {
        if (FixedInventory.isActive() || HideIndicator.isActive()) {
            cir.setReturnValue(false);
        }
    }
}

*///?}
