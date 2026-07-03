package top.fpsmaster.web.cef

//? if >=1.21.5 {

import com.mojang.blaze3d.opengl.GlStateManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

/**
 * Invalidates Minecraft's cached GL texture-binding state after external (non-GlStateManager) GL
 * calls, so the next bind through the cache is guaranteed to really execute.
 *
 * mcef-nova is Minecraft-agnostic: it creates, binds, uploads AND DELETES its browser textures with
 * raw LWJGL calls on the render thread (mainly inside CEF's DoMessageLoopWork pump at the head of
 * GameRenderer.render — see MixinGameRenderer). Raw calls bypass GlStateManager, which breaks its
 * per-unit binding cache in two distinct ways:
 *
 *  1. Stale binding: a raw glBindTexture leaves reality on the browser texture while the cache
 *     still names another texture. The next cached bind of that texture "cache-hits" and no-ops,
 *     and the following glyph-atlas upload lands in the wrong texture.
 *  2. Deleted-id aliasing (the killer): a raw glDeleteTextures never scrubs the cache (vanilla's
 *     _deleteTexture sets binding=-1 on every unit holding the id — raw deletion doesn't). GL then
 *     reuses the freed id for the next texture Minecraft creates — typically a new font-atlas page.
 *     That page's very first _bindTexture cache-hits against the stale entry and no-ops, so its
 *     storage allocation and every glyph upload go elsewhere: the entire page renders blank. This
 *     is the long-standing "p/q (a whole atlas page of glyphs) missing from all vanilla text" bug —
 *     macOS only, because GL 4.1 has no DSA and every texture write goes through the bind cache.
 *
 * invalidate() marks every texture unit's cached 2D binding as unknown (-1, vanilla's own "not
 * cached" sentinel), realigns the cached active-texture unit with reality, unbinds the polluted
 * unit for hygiene, and restores the pixel-store defaults mcef's dirty-rect uploads change. A -1
 * cache can never alias a real texture id, so every subsequent bind is real. Cost per call: a
 * dozen field writes + one glGetInteger — negligible, and it no-ops until a browser exists.
 */
object ExternalGlStateSync {
    /** Set once any browser exists; before that there is no external GL to repair. */
    @JvmStatic
    @Volatile
    var active = false

    // GlStateManager's per-unit binding cache. Reflection because the fields are private and there
    // is no public "invalidate" API; -1 mirrors vanilla's own _deleteTexture scrub sentinel.
    //? if 1.20.1 {
    /*private val texturesField = GlStateManager::class.java.getDeclaredField("TEXTURES").apply { isAccessible = true }
    private val textureStates = texturesField.get(null) as Array<*>
    private val bindingField = textureStates.first()!!.javaClass.getDeclaredField("binding").apply { isAccessible = true }
    *///? }

    @JvmStatic
    fun resync() {
        if (!active) {
            return
        }
        // 1) Invalidate EVERY unit's cached 2D binding — external code may have bound or deleted
        //    textures on whichever unit was really active, and a browser texture id legitimately
        //    cached earlier (our quad's sampler bind) becomes poison once mcef raw-deletes that id.
        //? if 1.20.1 {
        /*for (state in textureStates) {
            bindingField.setInt(state!!, -1)
        }
        *///? } else {
        for (state in GlStateManager.TEXTURES) {
            state.binding = -1
        }
        //? }
        // 2) Realign the cached active-texture unit with reality (mcef doesn't touch it, but keep
        //    the invariant cheap and absolute), and leave the unit cleanly unbound.
        GlStateManager._activeTexture(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE))
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        GlStateManager._bindTexture(0)
        // 3) Restore pixel-store defaults touched by dirty-rect (partial) frame uploads. These are
        //    uncached passthroughs in GlStateManager, so a raw restore cannot desync anything.
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
    }
}

//?}
