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
    private void fpsmaster$replaceTitleScreen(CallbackInfo ci) {
        boolean oobe = ConfigManager.INSTANCE.getOobeCompleted();

        if (Client.isCefReady()) {
            // CEF is ready — go straight to the OOBE (first run) or the webview main menu.
            if (!oobe) {
                Client.openOobe();
                ci.cancel();
                return;
            }
            if (Client.openMainMenu()) {
                ci.cancel();
            }
            return;
        }

        if (Client.isCefFailed()) {
            // CEF unavailable — keep the vanilla title screen usable (FPSMaster button added at TAIL).
            return;
        }

        // CEF not ready yet: load it in the background and show the (non-blocking) progress screen,
        // which hands off to the OOBE / main menu once ready.
        Client.beginCefLoad();
        Client.showCefLoadingScreen(oobe);
        ci.cancel();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fpsmaster$addMenuButton(CallbackInfo ci) {
        // Only reached when the webview main menu was NOT shown (CEF unavailable, or OOBE pending). Keep
        // the vanilla screen usable and still offer the FPSMaster ClickGUI.
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
