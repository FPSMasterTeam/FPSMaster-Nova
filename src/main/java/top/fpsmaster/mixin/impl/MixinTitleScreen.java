package top.fpsmaster.mixin.impl;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.Client;
import top.fpsmaster.config.ConfigManager;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {
    protected MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$replaceTitleScreen(CallbackInfo ci) {
        boolean oobe = ConfigManager.INSTANCE.getOobeCompleted();
        if (!oobe) {
            Client.openOobe();
            ci.cancel();
            return;
        }
        if (Client.openMainMenu()) {
            ci.cancel();
        }
    }
}
