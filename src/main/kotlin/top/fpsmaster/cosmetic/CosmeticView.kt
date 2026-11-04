package top.fpsmaster.cosmetic

import top.fpsmaster.mc

/**
 * "What cosmetic does entity N wear right now" — the single decision point every render layer
 * asks, on every Minecraft version.
 *
 * Only the local player wears FPSMaster cosmetics: other players' loadouts came from the FPSMaster
 * servers, which are gone. The entity id is the one key every era already has — vanilla
 * `EntityRenderState.id` on 1.21.5+, `Entity.getId()` on the immediate-mode versions.
 */
object CosmeticView {
    /** The local player's own back cosmetic is hidden in first person (it would fill the screen). */
    @JvmStatic
    fun hidesBackPiece(entityId: Int): Boolean = isLocal(entityId) &&
        !CosmeticManager.isPreviewing() &&
        mc.options.cameraType.isFirstPerson

    @JvmStatic
    fun rendersDragonWings(entityId: Int): Boolean = isLocal(entityId) &&
        (CosmeticManager.rendersDragonWings() ||
            CosmeticManager.isPreviewing() && CosmeticManager.selectsDragonWings())

    @JvmStatic
    fun rendersElytra(entityId: Int): Boolean = isLocal(entityId) && CosmeticManager.rendersElytra()

    /** Wing/elytra texture, or null to keep the vanilla one (builtin dragon wings included). */
    @JvmStatic
    fun wingTexture(entityId: Int): TextureId? = if (isLocal(entityId)) CosmeticManager.wingTexture() else null

    @JvmStatic
    fun capeTexture(entityId: Int): TextureId? = if (isLocal(entityId)) CosmeticManager.capeTexture() else null

    @JvmStatic
    fun wingScale(entityId: Int): Float = if (isLocal(entityId)) CosmeticManager.wingScale else 1f

    @JvmStatic
    fun animatesCape(entityId: Int): Boolean = isLocal(entityId) && CosmeticManager.animatesCape()

    @JvmStatic
    fun isLocal(entityId: Int): Boolean {
        val player = mc.player ?: return false
        return player.id == entityId
    }
}
