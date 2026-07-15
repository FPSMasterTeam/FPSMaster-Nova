package top.fpsmaster.web.cef

// 26.2 web-UI CPU texture upload. TextureFormat→GpuFormat, writeToTexture dropped the NativeImage.Format
// arg, and the 26.2 draw uses GuiGraphicsExtractor.blit(view, sampler, …) with the fixed RGBA GUI
// pipeline (no bgra swizzle shader). So unlike <26 (which uploads BGRA as-is and swizzles in the
// shader), here we swap B<->R into a reused direct buffer and upload as RGBA. That swap runs only per
// CEF paint (occasional), not per render frame. Exposes view + sampler for ClientBrowser's blit.
//? if >=26 {
/*import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import java.nio.ByteBuffer
import java.util.function.Supplier

class BrowserOwnedTexture : AutoCloseable {
    private var texture: GpuTexture? = null
    private var textureView: GpuTextureView? = null
    private var scratch: ByteBuffer? = null
    var width = 0
        private set
    var height = 0
        private set

    val ready: Boolean get() = texture != null
    val view: GpuTextureView? get() = textureView
    // Cached clamp-to-edge linear sampler (SamplerCache dedupes, so this getter is cheap per frame).
    val sampler: GpuSampler get() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    fun upload(buffer: ByteBuffer, frameWidth: Int, frameHeight: Int) {
        if (!RenderSystem.isOnRenderThread()) return
        if (frameWidth <= 0 || frameHeight <= 0) return
        val device = RenderSystem.getDevice()
        if (texture == null || width != frameWidth || height != frameHeight) {
            close()
            val created = device.createTexture(
                Supplier { "FPSMaster browser frame" },
                GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM,
                frameWidth,
                frameHeight,
                1,
                1
            )
            texture = created
            textureView = device.createTextureView(created)
            width = frameWidth
            height = frameHeight
        }
        val pixels = frameWidth * frameHeight * 4
        var dst = scratch
        if (dst == null || dst.capacity() < pixels) {
            dst = ByteBuffer.allocateDirect(pixels)
            scratch = dst
        }
        // Reset limit to capacity: the previous upload shrank it to that frame's size (below), and a
        // grow-after-shrink resize would otherwise fail every absolute put past the stale limit
        // (IndexOutOfBounds each frame -> permanently black webview after window resizes).
        dst.clear()
        // Absolute get/put so neither buffer's position is disturbed (CEF buffer starts at 0).
        var i = 0
        while (i < pixels) {
            val b = buffer.get(i)
            val g = buffer.get(i + 1)
            val r = buffer.get(i + 2)
            val a = buffer.get(i + 3)
            dst.put(i, r)
            dst.put(i + 1, g)
            dst.put(i + 2, b)
            dst.put(i + 3, a)
            i += 4
        }
        dst.position(0).limit(pixels)
        // writeToTexture(texture, buffer, mipLevel, depthOrLayer, x, y, width, height) — RGBA implied.
        device.createCommandEncoder().writeToTexture(texture!!, dst, 0, 0, 0, 0, frameWidth, frameHeight)
    }

    override fun close() {
        textureView?.close()
        textureView = null
        texture?.close()
        texture = null
        width = 0
        height = 0
    }
}*/
//?}

//? if >=1.21.5 && <26 {

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.gui.render.TextureSetup
import java.nio.ByteBuffer
import java.util.function.Supplier

/**
 * Browser frame texture OWNED by us and uploaded exclusively through Minecraft's GPU abstraction
 * (`CommandEncoder.writeToTexture`), replacing mcef's raw-GL upload path on 1.21.5+.
 *
 * Why: mcef-nova is Minecraft-agnostic and uploads CEF frames with raw LWJGL calls. Bisection
 * proved that letting that raw-GL path run corrupts subsequently-baked font-atlas glyphs (the
 * "p/q blank in all vanilla text" bug): the atlas dump showed the glyphs' cells allocated but the
 * pixels blank, and swallowing the paints (Chromium running, upload skipped) made the bug vanish.
 * Routing the pixels through Minecraft's own encoder removes every raw GL call from the paint
 * path, so there is nothing left to desync the GL state Minecraft's glyph uploads depend on.
 *
 * The CEF buffer is BGRA; we upload the bytes as-is with RGBA8 storage and let the existing
 * `pipeline/jcef/bgra_blurred_texture` shader swap R/B at draw time — no CPU swizzle.
 */
class BrowserOwnedTexture : AutoCloseable {
    private var texture: GpuTexture? = null
    private var textureView: GpuTextureView? = null

    var setup: TextureSetup = TextureSetup.noTexture()
        private set
    var width = 0
        private set
    var height = 0
        private set

    /** True once a frame has been uploaded and the texture is drawable. */
    val ready: Boolean
        get() = texture != null

    /**
     * Upload a full BGRA frame. Must be called on the render thread with the CEF paint buffer
     * still valid (CEF paints are delivered on the render thread via the DoMessageLoopWork pump);
     * frames arriving anywhere else are dropped rather than risking a cross-thread GL call.
     */
    fun upload(buffer: ByteBuffer, frameWidth: Int, frameHeight: Int) {
        if (!RenderSystem.isOnRenderThread()) {
            return
        }
        if (frameWidth <= 0 || frameHeight <= 0) {
            return
        }
        val device = RenderSystem.getDevice()
        if (texture == null || width != frameWidth || height != frameHeight) {
            close()
            val created = device.createTexture(
                Supplier { "FPSMaster browser frame" },
                GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
                TextureFormat.RGBA8,
                frameWidth,
                frameHeight,
                1,
                1
            )
            texture = created
            val view = device.createTextureView(created)
            textureView = view
            //? if >=1.21.11 {
            setup = TextureSetup.singleTexture(
                view,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false)
            )
            //?} else {
            /*created.setTextureFilter(FilterMode.LINEAR, false)
            created.setAddressMode(com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE)
            setup = TextureSetup.singleTexture(view)*/
            //?}
            width = frameWidth
            height = frameHeight
        }
        // BGRA bytes uploaded as-is; the jcef bgra shader swaps R/B at draw time. The buffer-overload
        // parameter type changed at 1.21.11 (ByteBuffer) from 1.21.5..1.21.10 (IntBuffer) — the
        // IntBuffer is just a view over the same bytes, GL reads raw memory either way.
        //? if >=1.21.11 {
        device.createCommandEncoder().writeToTexture(
            texture!!,
            buffer,
            NativeImage.Format.RGBA,
            0,
            0,
            0,
            0,
            frameWidth,
            frameHeight
        )
        //?} else {
        /*device.createCommandEncoder().writeToTexture(
            texture!!,
            buffer.asIntBuffer(),
            NativeImage.Format.RGBA,
            0,
            0,
            0,
            0,
            frameWidth,
            frameHeight
        )*/
        //?}
    }

    override fun close() {
        textureView?.close()
        textureView = null
        texture?.close()
        texture = null
        setup = TextureSetup.noTexture()
        width = 0
        height = 0
    }
}

//?}
