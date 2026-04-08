package top.fpsmaster.mixin.impl;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.auxiliary.NameProtect;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {
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
