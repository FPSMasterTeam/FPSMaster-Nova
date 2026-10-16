package top.fpsmaster.ui.kit;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * Publishes {@link NovaBlurCapture}'s offscreen colour attachment to the vanilla texture manager so
 * the blurred frame can be composited with the ordinary GUI blit path on every version.
 *
 * <p>The attachment is owned by the render target, so {@link #close()} deliberately does nothing:
 * releasing it here would delete a live framebuffer attachment behind the target's back.
 */
public final class NovaBlurTexture extends AbstractTexture {

    //? if >=1.21.11 {
    public void attach(RenderTarget target) {
        this.texture = target.getColorTexture();
        this.textureView = target.getColorTextureView();
        // The shared cached sampler: attach() runs on every framebuffer resize (every frame while
        // dragging a window edge), and a sampler created here was never closed.
        this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
    }

    @Override
    public void close() {
    }
    //?} else if >=1.21.5 {

    /*public void attach(RenderTarget target) {
        this.texture = target.getColorTexture();
        this.textureView = target.getColorTextureView();
        setFilter(true, false);
    }

    @Override
    public void close() {
    }
    *///?} else {

    /*public void attach(RenderTarget target) {
        this.id = target.getColorTextureId();
        setFilter(true, false);
    }

    @Override
    public void load(net.minecraft.server.packs.resources.ResourceManager resourceManager) {
    }

    @Override
    public void close() {
    }
    *///?}
}
