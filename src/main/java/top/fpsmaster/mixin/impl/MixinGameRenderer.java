package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ccbluex.liquidbounce.mcef.MCEF;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.LogUtil;
import top.fpsmaster.module.impl.optimization.NoHurtCam;
import top.fpsmaster.module.impl.render.MinimizedBobbing;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    public void hookGameRender(CallbackInfo callbackInfo) {
        if (MCEF.INSTANCE.isInitialized()) {
            try {
                MCEF.INSTANCE.getApp().getHandle().N_DoMessageLoopWork();
            } catch (Exception e) {
                LogUtil.logger.error("Failed to draw browser globally", e);
            }
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$cancelHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (NoHurtCam.isActive()) {
            ci.cancel();
        }
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private <T> T shouldBob(OptionInstance<Boolean> optionInstance) {
        return (T) (Object) (optionInstance.get() && !MinimizedBobbing.Companion.getWorking());
    }
}
