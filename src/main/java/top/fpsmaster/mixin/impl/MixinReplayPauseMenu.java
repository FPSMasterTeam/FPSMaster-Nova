package top.fpsmaster.mixin.impl;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.ui.ReplayOverlay;

/**
 * Sends Escape to the replay controls instead of the pause menu while a recording is playing.
 *
 * <p>The vanilla menu has nothing to offer here — Back to Game, Options, and a Disconnect that tears
 * the playback down sideways — while the one thing a viewer reaches for, the scrubber, needs a
 * cursor and so needs a screen. Escape is where a player already looks for that.
 *
 * <p>The decision, including the {@code screen == null} guard {@code pauseGame} makes for itself,
 * lives in {@link ReplayOverlay#openControls()}: reading and setting the current screen is
 * version-shaped and there is already a compat shim for it on the Kotlin side.
 *
 * <p>{@code pauseGame} has two callers and the {@code pauseOnly} flag does not separate them: the
 * key handler passes whether F3 is held, and the renderer's lost-focus auto-pause passes
 * {@code false} — the same value a bare Escape does. Window focus is what tells them apart.
 */
@Mixin(Minecraft.class)
public class MixinReplayPauseMenu {

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$replayControls(boolean pauseOnly, CallbackInfo ci) {
        // pauseOnly 只在 F3+ESC 时为 true（KeyboardHandler 把 F3 的按下状态直接传进来），那条
        // 路原版就是「只暂停、不弹菜单」，跟着不接管。
        if (pauseOnly) {
            return;
        }
        // 另一个调用点是窗口失焦自动暂停：GameRenderer.render 开头在 !isWindowActive() 且开了
        // pauseOnLostFocus 的时候调 pauseGame(false)，跟玩家真按下的 ESC 传的是同一个 false，
        // 光看参数分不出来。回放看到一半 alt-tab 一下就弹一整块控制台出来、回来还得先关掉它才
        // 能继续看，所以按窗口焦点分：没焦点的那次不是人按的。
        if (!Minecraft.getInstance().isWindowActive()) {
            return;
        }
        boolean tookOver;
        try {
            tookOver = ReplayOverlay.openControls();
        } catch (Throwable failure) {
            // 开不出来就让原版的暂停菜单顶上，总比 ESC 什么都不发生强。吞掉不记的话，
            // 「ESC 打不开回放控制」这种报障没有任何线索可查。
            top.fpsmaster.LogUtil.logger.warn("[replay] could not open the replay controls", failure);
            return;
        }
        if (tookOver) {
            ci.cancel();
        }
    }
}
