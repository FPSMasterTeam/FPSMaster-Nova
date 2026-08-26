package top.fpsmaster.ui.kit;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import top.fpsmaster.LogUtil;

/**
 * Produces the blurred backdrop Prism panels composite behind themselves.
 *
 * <p>One offscreen target the size of the framebuffer is kept for the whole session and only rebuilt
 * when the window resizes, so the per-frame cost is a texture copy plus a two-pass separable box
 * blur regardless of how many panels ask for it. The first panel of a frame triggers the capture;
 * the rest reuse it.
 *
 * <p>Everything here is best-effort: the first failure disables the effect for the session and
 * {@link top.fpsmaster.ui.kit.NovaBlur} falls back to the solid theme rather than leaving panels
 * transparent.
 */
public final class NovaBlurCapture {
    private static final String CAPTURE_KEY = "nova_blur_capture";

    private static RenderTarget target;
    private static NovaBlurTexture texture;
    private static PostChain chain;
    private static int width = -1;
    private static int height = -1;
    private static boolean unsupported;

    private NovaBlurCapture() {
    }

    public static boolean isUnsupported() {
        return unsupported;
    }

    public static int textureWidth() {
        return width;
    }

    public static int textureHeight() {
        return height;
    }

    /** Refreshes the blurred copy of everything drawn so far this frame. */
    public static boolean capture() {
        if (unsupported) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26 {
        /*RenderTarget main = minecraft.gameRenderer.mainRenderTarget();*/
        //?} else {
        RenderTarget main = minecraft.getMainRenderTarget();
        //?}
        int frameWidth = main.width;
        int frameHeight = main.height;
        if (frameWidth <= 0 || frameHeight <= 0) {
            return false;
        }
        try {
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.feature("screen-blur");
            }
            if (target == null || frameWidth != width || frameHeight != height) {
                rebuild(minecraft, frameWidth, frameHeight);
            }
            // Re-published every frame: a resource reload rebuilds the texture manager's map, and a
            // dropped registration would make the composite blit sample the missing-texture sprite.
            minecraft.getTextureManager().register(top.fpsmaster.UtilKt.identifier(CAPTURE_KEY), texture);
            copyFromMain(main);
            blur(minecraft);
            return true;
        } catch (Throwable failure) {
            LogUtil.logger.error("Panel blur is unavailable; using solid panels instead", failure);
            unsupported = true;
            release();
            return false;
        }
    }

    /** Drops the offscreen target. Called on resource reload and when the last Prism surface closes. */
    public static void release() {
        //? if <1.21.5 {
        /*if (chain != null) {
            chain.close();
        }*/
        //?}
        chain = null;
        if (target != null) {
            target.destroyBuffers();
            target = null;
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.released("panel-blur-target");
            }
        }
        texture = null;
        width = -1;
        height = -1;
    }

    private static void rebuild(Minecraft minecraft, int frameWidth, int frameHeight) throws Exception {
        release();
        //? if >=26 {
        /*target = new TextureTarget("fpsmaster_panel_blur", frameWidth, frameHeight, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);*/
        //?} else if >=1.21.5 {
        target = new TextureTarget("fpsmaster_panel_blur", frameWidth, frameHeight, false);
        //?} else {
        /*target = new TextureTarget(frameWidth, frameHeight, false, false);*/
        //?}
        width = frameWidth;
        height = frameHeight;

        texture = new NovaBlurTexture();
        texture.attach(target);

        //? if <1.21.5 {
        /*chain = new PostChain(
                minecraft.getTextureManager(),
                minecraft.getResourceManager(),
                target,
                top.fpsmaster.UtilKt.identifier("shaders/post/fpsmaster_blur.json"));
        chain.resize(frameWidth, frameHeight);*/
        //?}
    }

    //? if >=1.21.5 {
    private static void copyFromMain(RenderTarget main) {
        com.mojang.blaze3d.systems.RenderSystem.getDevice()
                .createCommandEncoder()
                .copyTextureToTexture(main.getColorTexture(), target.getColorTexture(), 0, 0, 0, 0, 0, width, height);
    }

    private static void blur(Minecraft minecraft) {
        var chainId = top.fpsmaster.UtilKt.identifier("nova_blur");
        PostChain postChain = minecraft.getShaderManager().getPostChain(chainId, java.util.Set.of());
        if (postChain == null) {
            throw new IllegalStateException("missing post effect " + chainId);
        }
        postChain.process(target, com.mojang.blaze3d.resource.GraphicsResourceAllocator.UNPOOLED);
    }
    //?} else {

    /*// Framebuffer row 0 is the bottom of the screen while the GUI blit that composites the capture
    // samples v=0 as the top, so the copy is flipped on the way in (the modern path flips in the
    // final post pass instead, where there is no framebuffer blit to piggyback on).
    private static void copyFromMain(RenderTarget main) {
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        org.lwjgl.opengl.GL30.glBlitFramebuffer(
                0, 0, main.width, main.height,
                0, height, width, 0,
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT,
                org.lwjgl.opengl.GL11.GL_LINEAR);
        main.bindWrite(false);
    }

    private static void blur(Minecraft minecraft) {
        // The chain was built against `target`, so "minecraft:main" resolves to the capture and the
        // live frame is never touched. Blur is time-invariant, hence the zero partial tick.
        chain.process(0f);
        minecraft.getMainRenderTarget().bindWrite(true);
    }*/
    //?}
}
