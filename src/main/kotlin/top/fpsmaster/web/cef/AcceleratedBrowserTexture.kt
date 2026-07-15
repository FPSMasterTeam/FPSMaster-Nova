package top.fpsmaster.web.cef

// 26.2 accelerated web-UI texture bridge. Three 26.2 obstacles, each solved here:
//   (1) GlTexture's protected ctor is `(int usage, String, GpuFormat, int w, int h, int depthOrLayers,
//       int mipLevels, int glId, FrameBufferCache)` — usage FIRST, the wrapped GL id 8th. The
//       FrameBufferCache comes reflectively off the GpuDevice's package-private GlDevice backend.
//       We wrap mcef's imported external GL id with it as a copy SOURCE.
//   (2) 26.2's deferred GUI renderer only samples textures the DEVICE created — an externally-wrapped id
//       records into the render state but draws blank. So we GPU-copy the imported frame into a device
//       texture (glCopyImageSubData, not the device copyTextureToTexture whose FBO blit rejects the
//       memory-object source) and draw that. The CPU never touches the pixels.
//   (3) The fixed RGBA blit has no swizzle, so a GL texture swizzle on the destination reads CEF's BGRA
//       back as RGBA (shader-free).
// Frame delivery requires the CEF message loop to be pumped ON THE RENDER THREAD
// (-Dmcef.pumpOnRenderThread=true, set by Client.configureHost when hardware-acceleration is enabled);
// with mcef's dedicated CEF thread, CEF stops after a single accelerated frame. See
// docs/26.2-cef-zerocopy-todo.md.
// [[nova-mc26-unobfuscated-build]]
//? if >=26 {
/*import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.opengl.FrameBufferCache
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL33
import org.lwjgl.opengl.GL43
import top.fpsmaster.logger
import java.lang.reflect.Constructor
import java.util.function.Supplier

/**
 * Draws mcef-nova's ZERO-COPY imported CEF GPU texture on 26.2 by GPU-copying it into a device-owned
 * texture the deferred GUI blit can sample (see the file header for why a straight wrap draws blank).
 *
 * The imported id is mcef's — the [com.mojang.blaze3d.opengl.GlTexture] we wrap it in as the copy source
 * MUST NEVER be closed (that would glDeleteTextures an id mcef still manages). Only our destination texture
 * is ours to [close]. If wrapping ever fails on an unexpected blaze3d layout, [isSupported] flips false and
 * the caller falls back to the CPU path.
 */
class AcceleratedBrowserTexture {

    private var srcTexId = 0
    private var srcTexture: GpuTexture? = null
    private var dstTexture: GpuTexture? = null
    private var dstView: GpuTextureView? = null
    private var dstWidth = 0
    private var dstHeight = 0

    /** Clamp-to-edge linear sampler (SamplerCache dedupes, so this getter is cheap per frame). */
    val sampler: GpuSampler
        get() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false)

    /**
     * GPU-copy CEF's imported [texId] frame ([width]x[height]) into our device-owned texture and return
     * its view. Returns null if wrapping isn't possible.
     *
     * Why a copy and not a straight wrap-and-draw: 26.2's deferred GUI renderer only samples textures the
     * device created/owns — an externally-wrapped GL id (mcef's imported texture) records into the render
     * state but draws blank at flush. So we wrap the external id as the copy SOURCE and blit it, GPU-side,
     * into a device texture the deferred blit can sample. The CPU never touches the pixels; the CEF→GL
     * import stays zero-copy and the BGRA→RGBA swap is a free GL texture swizzle on the destination.
     */
    fun setup(texId: Int, width: Int, height: Int): GpuTextureView? {
        if (texId == 0 || width <= 0 || height <= 0 || !isSupported) {
            return null
        }
        return try {
            copyIntoOwnedTexture(texId, width, height)
        } catch (t: Throwable) {
            logger.error("Failed to draw accelerated browser texture (id={}, {}x{})", texId, width, height, t)
            supported = false
            null
        }
    }

    private fun copyIntoOwnedTexture(texId: Int, width: Int, height: Int): GpuTextureView {
        val device = RenderSystem.getDevice()
        // Wrap mcef's imported external GL id as a GlTexture (copy SOURCE); cache while the id is unchanged.
        if (texId != srcTexId || srcTexture == null) {
            srcTexture = wrapExternalTexture(texId, width, height)
            srcTexId = texId
        }
        // Device-owned destination the deferred GUI blit can actually sample; recreate on size change. Its
        // GL swizzle reads CEF's BGRA bytes back as RGBA at sample time (no CPU swizzle, no shader).
        if (dstTexture == null || dstWidth != width || dstHeight != height) {
            close()
            val created = device.createTexture(
                Supplier { "FPSMaster browser accel" },
                GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM, width, height, 1, 1
            )
            dstTexture = created
            dstView = device.createTextureView(created)
            dstWidth = width
            dstHeight = height
            applyBgraSwizzle(created)
        }
        // Direct GPU image copy of CEF's imported frame into our texture. We use glCopyImageSubData (not
        // the device's copyTextureToTexture, which drives an FBO blit — the imported memory-object texture
        // can't be an FBO colour attachment, giving GL_INVALID_FRAMEBUFFER_OPERATION). Image copy needs no
        // FBO and no bind, and the CPU never touches the pixels.
        GL43.glCopyImageSubData(
            glId(srcTexture!!), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
            glId(dstTexture!!), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
            width, height, 1
        )
        return dstView!!
    }

    private fun wrapExternalTexture(texId: Int, width: Int, height: Int): GpuTexture {
        // GlTexture(int usage, String label, GpuFormat, int width, int height, int depthOrLayers,
        //           int mipLevels, int glId, FrameBufferCache) — usage FIRST, the existing GL id 8th
        //           (matches LiquidBounce's DirectGlTexture super() call). Wraps the id, no glGen.
        // frameBufferCache() lives on the package-private GlDevice backend; reach it reflectively.
        return glTextureCtor().newInstance(
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_SRC,
            "FPSMaster browser accel src",
            GpuFormat.RGBA8_UNORM,
            width,
            height,
            1,
            1,
            texId,
            frameBufferCache()
        ) as GpuTexture
    }

    /**
     * Tear down our device-owned copy texture. NEVER touches the source GL id — that's mcef's (closing it
     * would glDeleteTextures an id mcef still manages). Called on size change and browser teardown.
     */
    fun close() {
        dstView?.close()
        dstView = null
        dstTexture?.close()
        dstTexture = null
        dstWidth = 0
        dstHeight = 0
    }

    /** Browser teardown: drop our copy texture and forget the external id. */
    fun reset() {
        close()
        srcTexId = 0
        srcTexture = null
    }

    /**
     * Swap the destination texture's R/B channels via a GL texture swizzle so CEF's BGRA copy samples as
     * RGBA — the free, shader-less BGRA fix (the fixed GUI blit has no swizzle). glId() is public but its
     * declaring class (GlTexture) is package-private, so the reflective invoke needs setAccessible.
     */
    private fun applyBgraSwizzle(texture: GpuTexture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0)
        GlStateManager._bindTexture(glId(texture))
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL33.GL_TEXTURE_SWIZZLE_R, GL11.GL_BLUE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL33.GL_TEXTURE_SWIZZLE_B, GL11.GL_RED)
    }

    companion object {
        @Volatile
        private var supported = true

        /** GlTexture.glId() is public but its declaring class is package-private — invoke needs access. */
        private fun glId(texture: GpuTexture): Int =
            texture.javaClass.getMethod("glId").apply { isAccessible = true }.invoke(texture) as Int

        /** Whether external-GL-texture wrapping is available (false once a build attempt fails). */
        val isSupported: Boolean
            get() = supported

        private var ctorCache: Constructor<*>? = null

        /** The GlDevice backend's [FrameBufferCache], read from GpuDevice's private `backend` field. */
        private fun frameBufferCache(): FrameBufferCache {
            val device = RenderSystem.getDevice()
            val backendField = device.javaClass.getDeclaredField("backend")
            backendField.isAccessible = true
            val backend = backendField.get(device)
            // frameBufferCache() is public but its declaring class (GlDevice) is package-private, so
            // reflective invoke from here needs setAccessible to pass the access check.
            val method = backend.javaClass.getMethod("frameBufferCache").apply { isAccessible = true }
            return method.invoke(backend) as FrameBufferCache
        }

        private fun glTextureCtor(): Constructor<*> {
            ctorCache?.let { return it }
            val cls = Class.forName("com.mojang.blaze3d.opengl.GlTexture")
            val ctor = cls.getDeclaredConstructor(
                Int::class.javaPrimitiveType,
                String::class.java,
                GpuFormat::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                FrameBufferCache::class.java
            )
            ctor.isAccessible = true
            ctorCache = ctor
            return ctor
        }

        /**
         * Probe once whether the external-id GlTexture wrapping constructor exists. Called during
         * acceleration gating so a blaze3d layout we can't wrap disables zero-copy up front (CPU path).
         */
        fun probe(): Boolean {
            if (!supported) return false
            return try {
                glTextureCtor()
                true
            } catch (t: Throwable) {
                logger.warn("Accelerated browser texture wrapping unavailable; GPU zero-copy disabled: {}", t.toString())
                supported = false
                false
            }
        }
    }
}
*///?}

//? if >=1.21.5 && <26 {

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.gui.render.TextureSetup
import top.fpsmaster.logger
import java.lang.reflect.Constructor

/**
 * Wraps a raw OpenGL texture id — one mcef-nova imported ZERO-COPY from CEF's GPU shared texture on
 * Windows/Linux — into a blaze3d [TextureSetup] so the web UI can be drawn straight from the browser's
 * own GPU texture, with no CPU copy.
 *
 * mcef owns the GL id's lifetime (it imports a fresh texture each accelerated frame and frees the one
 * the render thread stopped displaying). So the [com.mojang.blaze3d.opengl.GlTexture] we build here
 * MUST NEVER be closed — closing it would call glDeleteTextures on an id mcef still manages (double
 * free). We therefore build a lightweight throwaway wrapper each frame and simply drop it; the GL id
 * is untouched by GC.
 *
 * GlTexture's constructor is `protected`, so it's reached by reflection. If that ever fails (an
 * unexpected blaze3d layout), [isSupported] reports false and the caller falls back to the CPU path.
 */
class AcceleratedBrowserTexture {

    private var lastTexId = 0
    private var cachedSetup: TextureSetup = TextureSetup.noTexture()

    /**
     * Build (or reuse) a [TextureSetup] that samples [texId] (a BGRA8-in-RGBA8 GL texture of
     * [width]x[height]). Returns [TextureSetup.noTexture] if wrapping isn't possible.
     */
    fun setup(texId: Int, width: Int, height: Int): TextureSetup {
        if (texId == 0 || width <= 0 || height <= 0 || !isSupported) {
            return TextureSetup.noTexture()
        }
        // The imported id changes every accelerated frame; only rebuild when it actually changes.
        if (texId == lastTexId && cachedSetup !== TextureSetup.noTexture()) {
            return cachedSetup
        }
        val setup = try {
            buildSetup(texId, width, height)
        } catch (t: Throwable) {
            logger.error("Failed to wrap accelerated browser texture (id={}, {}x{})", texId, width, height, t)
            supported = false
            TextureSetup.noTexture()
        }
        lastTexId = texId
        cachedSetup = setup
        return setup
    }

    private fun buildSetup(texId: Int, width: Int, height: Int): TextureSetup {
        // GlTexture(int usage, String label, TextureFormat format, int width, int height,
        //           int depthOrLayers, int mipLevels, int glId) — usage FIRST, the existing GL id
        // LAST (verified against GlDevice.createTexture bytecode on 1.21.8/1.21.11). Wraps the id.
        val glTexture = glTextureCtor().newInstance(
            GpuTexture.USAGE_TEXTURE_BINDING,
            "FPSMaster browser accel",
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1,
            texId
        ) as GpuTexture

        // Reflect GlTextureView directly (rather than device.createTextureView) so nothing on the GL
        // device side retains a per-frame view — we build a throwaway wrapper and drop it.
        val view: GpuTextureView = glTextureViewCtor(glTexture.javaClass).newInstance(glTexture, 0, 1) as GpuTextureView
        //? if >=1.21.11 {
        return TextureSetup.singleTexture(
            view,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false)
        )
        //?} else {
        /*glTexture.setTextureFilter(FilterMode.LINEAR, false)
        glTexture.setAddressMode(com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE)
        return TextureSetup.singleTexture(view)*/
        //?}
    }

    /** Called when the browser is torn down; drop the cached wrapper (does NOT free the GL id). */
    fun reset() {
        lastTexId = 0
        cachedSetup = TextureSetup.noTexture()
    }

    companion object {
        @Volatile
        private var supported = true

        /** Whether external-GL-texture wrapping is available (false once a build attempt fails). */
        val isSupported: Boolean
            get() = supported

        private var ctorCache: Constructor<*>? = null
        private var viewCtorCache: Constructor<*>? = null

        private fun glTextureCtor(): Constructor<*> {
            ctorCache?.let { return it }
            val cls = Class.forName("com.mojang.blaze3d.opengl.GlTexture")
            val ctor = cls.getDeclaredConstructor(
                Int::class.javaPrimitiveType,
                String::class.java,
                TextureFormat::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            ctor.isAccessible = true
            ctorCache = ctor
            return ctor
        }

        private fun glTextureViewCtor(glTextureClass: Class<*>): Constructor<*> {
            viewCtorCache?.let { return it }
            val cls = Class.forName("com.mojang.blaze3d.opengl.GlTextureView")
            // GlTextureView(GlTexture texture, int baseMipLevel, int mipLevels)
            val ctor = cls.getDeclaredConstructor(
                glTextureClass,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            ctor.isAccessible = true
            viewCtorCache = ctor
            return ctor
        }

        /**
         * Probe once whether the GlTexture wrapping constructor exists. Called during acceleration
         * gating so a blaze3d layout we can't wrap disables zero-copy up front (CPU path instead).
         */
        fun probe(): Boolean {
            if (!supported) return false
            return try {
                val texCls = glTextureCtor().declaringClass
                glTextureViewCtor(texCls)
                true
            } catch (t: Throwable) {
                logger.warn("Accelerated browser texture wrapping unavailable; GPU zero-copy disabled: {}", t.toString())
                supported = false
                false
            }
        }
    }
}

//?}
