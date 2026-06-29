package top.fpsmaster.cefbridge;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.Client;

/**
 * Minimal client tick/shutdown driver for the 1.20.1 feasibility build. The full 1.21.5+
 * {@code MixinMinecraft} lives in the gated-out {@code mixin/impl} package, so without this the
 * client keybind that opens the CEF browser ({@code Client.tick() -> onTick()}) would never run.
 * Kept tiny on purpose — only the hooks the browser entry point needs.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftCef {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void fpsmaster$tick(boolean renderLevel, CallbackInfo callback) {
        Client.tick();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void fpsmaster$shutdown(CallbackInfo callback) {
        Client.shutdown();
    }
}
