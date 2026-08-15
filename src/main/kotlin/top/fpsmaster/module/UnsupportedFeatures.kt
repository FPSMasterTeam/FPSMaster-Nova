package top.fpsmaster.module

/**
 * Modules whose backing render features are NOT implemented on the current Minecraft version and
 * are therefore force-disabled and shown greyed-out in the Web UI ("unavailable on this version").
 *
 * Only 1.21.11 is fully featured. Every other version node has a subset of the complex render
 * mixins gated out (see `docs/multiversion.md` and the per-version `fpsmaster-<ver>.mixins.json`
 * files); the identities below are the user-facing modules those gated mixins back.
 *
 * The set is resolved at COMPILE time via Stonecutter `//? if` predicates — each version node keeps
 * only its own branch. On the active 1.21.11 node every branch is inactive, so the set is empty.
 */
object UnsupportedFeatures {
    val ids: Set<String> = buildSet {
        // 1.19.2 — legacy render: cape/wings layers, motion-blur post pass, hitbox debug, screen
        // effects and item-entity render are all gated out.
        //? if <1.20 {
        /*addAll(listOf("wavy-cape", "dragon-wings", "motion-blur", "hitboxes", "better-screen", "item-physics"))*/
        //?}
        // 1.20.1 — legacy render baseline: only hitbox debug, motion-blur post pass and item-entity
        // render-state are gated out; cape/wings/screen work.
        //? if >=1.20 && <1.21 {
        /*addAll(listOf("hitboxes", "motion-blur", "item-physics"))
        *///?}
        // 1.21.1 — legacy render + extra deltas: cape, wings, motion-blur, fishing line, hitboxes,
        // block overlay, screen effects and item-entity render are gated out.
        //? if >=1.21 && <1.21.5 {
        /*addAll(listOf("wavy-cape", "dragon-wings", "motion-blur", "better-fishing-rod", "hitboxes", "block-overlay", "item-physics", "better-screen"))*/
        //?}
        // 1.21.8 — submit-node era: cape, wings, motion-blur, fishing line, hitboxes, block overlay
        // and item-entity render are gated out (screen effects work here).
        //? if >=1.21.5 && <1.21.11 {
        /*addAll(listOf("wavy-cape", "dragon-wings", "motion-blur", "better-fishing-rod", "hitboxes", "block-overlay", "item-physics"))*/
        //?}
        // 26.2 — unobfuscated / deferred-render bring-up: wings, block overlay, screen effects and
        // item-entity render gated out via mixins; NoHurtCam / SmoothZoom / MotionBlur QoL deferred.
        //? if >=26 {
        /*addAll(listOf("dragon-wings", "block-overlay", "better-screen", "item-physics", "motion-blur", "no-hurt-cam"))
        *///?}
    }

    fun isUnsupported(identity: String): Boolean = identity in ids
}
