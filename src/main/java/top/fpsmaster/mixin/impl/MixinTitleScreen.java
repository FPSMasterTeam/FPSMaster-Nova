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

    @Inject(method = "init", at = @At("TAIL"))
    private void fpsmaster$replaceTitleScreen(CallbackInfo ci) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.execute(() -> {
            //? if >=26.2 {
            /*if (minecraft.gui.screen() != (Object) this) {
            *///?} else {
            if (minecraft.screen != (Object) this) {
            //?}
                return;
            }
            if (!ConfigManager.INSTANCE.getOobeCompleted()) {
                Client.openOobe();
            } else {
                Client.openMainMenu();
            }
        });
    }
}
