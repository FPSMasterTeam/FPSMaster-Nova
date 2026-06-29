package top.fpsmaster.web.cef;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * 1.21.5+ CEF render bridge.
 *
 * <p>The version-agnostic {@code mcef-nova} library hands us nothing but a raw OpenGL texture id
 * ({@code MCEFRenderer.getTextureId()}). This class is the per-version glue that wraps that id into
 * the {@link TextureSetup} the modern GUI render pipeline ({@code submitGuiElement}) consumes. It is
 * a direct port of the wrapper MCEF used to carry internally, moved here because it is the only
 * Minecraft-version-specific part of browser rendering. The 1.20.x source set draws the same texture
 * id with an immediate-mode quad instead and does not use this class.
 */
public class BrowserDirectTexture extends AbstractTexture {
    private int wrappedId = 0;
    private int width;
    private int height;
    private TextureSetup textureSetup = TextureSetup.noTexture();

    public BrowserDirectTexture() {
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false);
    }

    /**
     * Point this texture at an existing GL texture id. We do not own the id (MCEFRenderer does), so
     * we never delete it. Re-wraps only when the id or size actually changed, to avoid allocating a
     * fresh GpuTextureView every frame on the stable CPU-paint path.
     */
    public TextureSetup wrap(int textureId, int width, int height) {
        if (textureId == wrappedId && width == this.width && height == this.height && textureId != 0) {
            return textureSetup;
        }
        if (textureId > 0) {
            this.texture = new DirectGlTexture(textureId, width, height);
            if (this.textureView != null) {
                this.textureView.close();
            }
            this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
            this.textureSetup = TextureSetup.singleTexture(this.getTextureView(), this.getSampler());
            this.wrappedId = textureId;
            this.width = width;
            this.height = height;
        } else {
            this.texture = null;
            this.textureView = null;
            this.textureSetup = TextureSetup.noTexture();
            this.wrappedId = 0;
        }
        return this.textureSetup;
    }

    @Override
    public void close() {
        this.texture = null;
        if (this.textureView != null) {
            this.textureView.close();
            this.textureView = null;
        }
        this.textureSetup = TextureSetup.noTexture();
        this.wrappedId = 0;
    }

    /**
     * A GlTexture that wraps an existing OpenGL id without owning its lifecycle.
     */
    static class DirectGlTexture extends GlTexture {
        private final int width;
        private final int height;

        protected DirectGlTexture(int textureId, int width, int height) {
            super(
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
                "MCEF Direct Texture", TextureFormat.RGBA8, width, height, 1, 1, textureId
            );
            this.width = width;
            this.height = height;
            this.closed = false;
        }

        @Override
        public void close() {
            // Do not delete the texture - we do not own it.
            this.closed = true;
        }

        @Override
        public int getWidth(int mipLevel) {
            return width >> mipLevel;
        }

        @Override
        public int getHeight(int mipLevel) {
            return height >> mipLevel;
        }
    }
}
