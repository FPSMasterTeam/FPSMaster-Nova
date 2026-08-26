package top.fpsmaster.cosmetic

import top.fpsmaster.mc
import java.util.UUID

/**
 * "What cosmetic does entity N wear right now" — the single decision point every render layer
 * asks, on every Minecraft version.
 *
 * Render layers used to compare the render state's entity id against the local player's and bail
 * out for everybody else, which is why other FPSMaster players were invisible to each other. They
 * now route through here: the local player keeps the preview-aware [CosmeticManager] state, and any
 * other player resolves through [CosmeticLoadoutCache] by Mojang-verified UUID.
 *
 * The entity id is the one key every era already has — vanilla `EntityRenderState.id` on 1.21.5+,
 * `Entity.getId()` on the immediate-mode versions.
 */
object CosmeticView {
    /**
     * The local player's own back cosmetic is hidden in first person (it would fill the screen);
     * everybody else's stays visible, and preview screens always show it.
     */
    @JvmStatic
    fun hidesBackPiece(entityId: Int): Boolean = isLocal(entityId) &&
        !CosmeticManager.isPreviewing() &&
        mc.options.cameraType.isFirstPerson

    @JvmStatic
    fun rendersDragonWings(entityId: Int): Boolean = if (isLocal(entityId)) {
        CosmeticManager.rendersDragonWings() ||
            CosmeticManager.isPreviewing() && CosmeticManager.selectsDragonWings()
    } else {
        remote(entityId)?.rendersDragonWings == true
    }

    @JvmStatic
    fun rendersElytra(entityId: Int): Boolean = if (isLocal(entityId)) {
        CosmeticManager.rendersElytra()
    } else {
        remote(entityId)?.rendersElytra == true
    }

    /** Wing/elytra texture, or null to keep the vanilla one (builtin dragon wings included). */
    @JvmStatic
    fun wingTexture(entityId: Int): TextureId? = if (isLocal(entityId)) {
        CosmeticManager.wingTexture()
    } else {
        remote(entityId)?.backItem?.let { CosmeticManager.textureFor(it.id.toString()) }
    }

    @JvmStatic
    fun capeTexture(entityId: Int): TextureId? = if (isLocal(entityId)) {
        CosmeticManager.capeTexture()
    } else {
        remote(entityId)?.capeItem?.let { CosmeticManager.textureFor(it.id.toString()) }
    }

    @JvmStatic
    fun wingScale(entityId: Int): Float = if (isLocal(entityId)) {
        CosmeticManager.wingScale
    } else {
        remote(entityId)?.wingScale ?: 1f
    }

    @JvmStatic
    fun animatesCape(entityId: Int): Boolean = if (isLocal(entityId)) {
        CosmeticManager.animatesCape()
    } else {
        remote(entityId)?.capeAnimationEnabled == true
    }

    @JvmStatic
    fun isLocal(entityId: Int): Boolean {
        val player = mc.player ?: return false
        return player.id == entityId
    }

    private fun remote(entityId: Int): CosmeticLoadoutCache.Loadout? =
        uuidOf(entityId)?.let(CosmeticLoadoutCache::get)

    private fun uuidOf(entityId: Int): UUID? = mc.level?.getEntity(entityId)?.uuid
}
