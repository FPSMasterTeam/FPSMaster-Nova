package top.fpsmaster.mixin.impl;

// 1.21.5~1.21.10 的 GuiRenderer 与 1.21.11 同形（bufferSource + pictureInPictureRenderers
// 两个 private final 字段，PIP state 也在同一个包），这一档共用同一份注册。
//? if >=1.21.5 && <26 {

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.cosmetic.WingPreviewRenderer;

@Mixin(GuiRenderer.class)
public abstract class MixinGuiRenderer {
    @Shadow @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Shadow @Final @Mutable
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fpsmaster$registerWingPreview(CallbackInfo ci) {
        WingPreviewRenderer renderer = new WingPreviewRenderer(this.bufferSource);
        Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> renderers =
                new HashMap<>(this.pictureInPictureRenderers);
        renderers.put(renderer.getRenderStateClass(), renderer);
        this.pictureInPictureRenderers = renderers;
    }
}

//?} else if >=26 {

/*import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.cosmetic.WingPreviewRenderer;

@Mixin(GuiRenderer.class)
public abstract class MixinGuiRenderer {
    @Shadow @Final @Mutable
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fpsmaster$registerWingPreview(CallbackInfo ci) {
        WingPreviewRenderer renderer = new WingPreviewRenderer();
        Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> renderers =
                new HashMap<>(this.pictureInPictureRenderers);
        renderers.put(renderer.getRenderStateClass(), renderer);
        this.pictureInPictureRenderers = renderers;
    }
}
*///?}
