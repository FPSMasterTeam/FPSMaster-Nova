package top.fpsmaster.mixin.impl;

// 26 把 Window.getAppropriateLineWidth() 抽成了渲染状态里的一个字段
// WindowRenderState.appropriateLineWidth，调用点从 INVOKE 变成 GETFIELD。
//? if >=26 {

/*import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.optimization.BetterFishingRod;

@Mixin(FishingHookRenderer.class)
public class MixinFishingHookRenderer {
    @Redirect(
            method = "submit",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/state/WindowRenderState;appropriateLineWidth:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float fpsmaster$adjustFishingLineWidth(WindowRenderState state) {
        return BetterFishingRod.resolveLineWidth(state.appropriateLineWidth);
    }
}

*///?} elif >=1.21.11 {

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.optimization.BetterFishingRod;

@Mixin(FishingHookRenderer.class)
public class MixinFishingHookRenderer {
    @Redirect(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/Window;getAppropriateLineWidth()F"
            )
    )
    private float fpsmaster$adjustFishingLineWidth(Window window) {
        return BetterFishingRod.resolveLineWidth(window.getAppropriateLineWidth());
    }
}

//?} else {

/*import net.minecraft.client.renderer.FpsmasterFishingLine;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.module.impl.optimization.BetterFishingRod;

@Mixin(FishingHookRenderer.class)
public class MixinFishingHookRenderer {
    // Before 1.21.11 there is no Window.getAppropriateLineWidth; FishingHookRenderer.render draws the
    // string with RenderType.lineStrip(), whose width comes from an empty LineStateShard. Redirect it
    // to a width-matched LINE_STRIP type built by FpsmasterFishingLine (which lives in the renderer
    // package to reach the protected composite). Identical on 1.19.2, 1.20.1, 1.21.1 and 1.21.8.
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;lineStrip()Lnet/minecraft/client/renderer/RenderType;")
    )
    private RenderType fpsmaster$fishingLineWidth() {
        if (!BetterFishingRod.isActive()) {
            return RenderType.lineStrip();
        }
        return FpsmasterFishingLine.lineStrip(BetterFishingRod.resolveLineWidth(1.0F));
    }
}

*///?}
