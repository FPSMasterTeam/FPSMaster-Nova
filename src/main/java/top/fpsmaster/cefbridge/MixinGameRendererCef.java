package top.fpsmaster.cefbridge;

import net.ccbluex.liquidbounce.mcef.MCEF;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.LogUtil;

/**
 * Minimal CEF message-loop driver for the 1.20.1 feasibility build. The full 1.21.5+
 * {@code MixinGameRenderer} is excluded on 1.20.1 (it targets the post-effect/render rewrite);
 * this keeps only the message-loop pump the browser needs to update its texture.
 *
 * <p>Lives outside {@code top.fpsmaster.mixin.impl} on purpose: the 1.20.1 build excludes that whole
 * package, so this 1.20.1-only mixin must sit in its own package to survive compilation.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererCef {
    @Inject(method = "render", at = @At("HEAD"))
    public void fpsmaster$hookGameRender(CallbackInfo callbackInfo) {
        if (MCEF.INSTANCE.isInitialized()) {
            try {
                MCEF.INSTANCE.getApp().getHandle().N_DoMessageLoopWork();
            } catch (Exception e) {
                LogUtil.logger.error("Failed to draw browser globally", e);
            }
        }
    }
}
