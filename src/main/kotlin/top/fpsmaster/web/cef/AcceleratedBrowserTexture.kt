package top.fpsmaster.web.cef

//? if >=1.21.5 {

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
        // GlTexture(int glId, String label, TextureFormat format, int width, int height,
        //           int depthOrLayers, int mipLevels, int usage) — wraps an EXISTING id (no glGen).
        val glTexture = glTextureCtor().newInstance(
            texId,
            "FPSMaster browser accel",
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1,
            GpuTexture.USAGE_TEXTURE_BINDING
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
