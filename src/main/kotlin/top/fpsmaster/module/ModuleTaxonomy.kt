package top.fpsmaster.module

/** ClickGUI page a module is surfaced on. MUSIC has no modules (bespoke media page). */
enum class Page { FEATURES, PERFORMANCE, GAME, CLIENT }

/** Filter tag used on the FEATURES page. */
enum class FeatureTag { DISPLAY, VISUAL, UTILITY, NONE }

/**
 * Where a module appears in the ClickGUI. [group] is a non-empty merge-group id when several modules
 * should render as a single card (e.g. the item displays under "item-display"). [order] orders modules
 * within a page/group.
 */
data class ModulePlacement(
    val page: Page = Page.FEATURES,
    val tag: FeatureTag = FeatureTag.NONE,
    val group: String = "",
    val order: Int = 0
)

/**
 * Single source of truth mapping module identity -> ClickGUI placement (page / filter tag / merge
 * group). Keeping this in one object avoids touching all ~57 module files and makes the ClickGUI
 * reorganisation reviewable in one place. Modules not listed fall back to FEATURES/NONE.
 */
object ModuleTaxonomy {
    private val placements: Map<String, ModulePlacement> = buildMap {
        // ---- GAME: vanilla enhancements, rendered as sections ----
        listOf(
            "better-chat", "better-screen", "hide-indicator", "custom-titles", "scoreboard",
            "tab-overlay", "better-fishing-rod", "animation", "fixed-inventory", "no-hit-delay", "no-hurt-cam"
        ).forEachIndexed { i, id -> put(id, ModulePlacement(Page.GAME, order = i)) }

        // ---- PERFORMANCE ----
        put("optimization", ModulePlacement(Page.PERFORMANCE))

        // ---- CLIENT: merged into the bespoke client-settings page ----
        put("client-settings", ModulePlacement(Page.CLIENT, order = 0))
        put("clickgui", ModulePlacement(Page.CLIENT, order = 1))

        // ---- FEATURES / DISPLAY ----
        // Item displays merge into one "item-display" card (three sub-sections).
        put("armor-display", ModulePlacement(Page.FEATURES, FeatureTag.DISPLAY, "item-display", 0))
        put("inventory-display", ModulePlacement(Page.FEATURES, FeatureTag.DISPLAY, "item-display", 1))
        put("item-count-display", ModulePlacement(Page.FEATURES, FeatureTag.DISPLAY, "item-display", 2))
        listOf(
            "fps-display", "cps-display", "ping-display", "reach-display", "coords-display",
            "direction-display", "combo-display", "player-display", "keystrokes", "potion-display",
            "mods-list", "target-display", "mini-map", "block-indicator"
        ).forEach { put(it, ModulePlacement(Page.FEATURES, FeatureTag.DISPLAY)) }

        // ---- FEATURES / VISUAL ----
        listOf(
            "crosshair", "hitboxes", "block-overlay", "hit-color", "damage-indicator", "dragon-wings",
            "fire-modifier", "motion-blur", "more-particles", "wavy-cape", "item-physics", "clean-view",
            "minimized-bobbing", "full-bright"
        ).forEach { put(it, ModulePlacement(Page.FEATURES, FeatureTag.VISUAL)) }

        // ---- FEATURES / UTILITY ----
        listOf(
            "auto-gg", "name-protect", "level-tag", "raw-input", "tnt-timer", "time-changer",
            "sound-modifier", "custom-fov", "sprint", "free-look", "smooth-zoom", "particles-modifier"
        ).forEach { put(it, ModulePlacement(Page.FEATURES, FeatureTag.UTILITY)) }
    }

    fun of(identity: String): ModulePlacement = placements[identity] ?: ModulePlacement()
}
