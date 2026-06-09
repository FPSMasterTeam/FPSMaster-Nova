package top.fpsmaster.mixin.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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
    private void fpsmaster$openOobe(CallbackInfo ci) {
        if (!ConfigManager.INSTANCE.getOobeCompleted()) {
            Client.openOobe();
            ci.cancel();
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fpsmaster$addMenuButton(CallbackInfo ci) {
        if (!ConfigManager.INSTANCE.getOobeCompleted()) {
            return;
        }

        this.addRenderableWidget(
                Button.builder(Component.literal("FPSMaster"), button -> Client.openClickGui())
                        .bounds(2, this.height - 34, 98, 20)
                        .build()
        );
    }
}
