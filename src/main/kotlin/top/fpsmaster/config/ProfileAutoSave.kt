package top.fpsmaster.config

/**
 * Persists ClickGUI edits into the active profile as they happen.
 *
 * Discrete edits (toggle, choice, key, list, text) go straight to disk through [save] so the last
 * completed interaction survives a crash. Continuous edits (slider and colour drags) fire every frame,
 * so [coalesce] writes at most once per [WINDOW_MS]; [flush] on screen close persists the final value.
 */
object ProfileAutoSave {
    private const val WINDOW_MS = 500L
    private var pending = false
    private var lastSaveAt = 0L

    fun save() {
        pending = false
        lastSaveAt = System.currentTimeMillis()
        ConfigManager.saveActive()
    }

    fun coalesce() {
        if (System.currentTimeMillis() - lastSaveAt >= WINDOW_MS) {
            save()
        } else {
            pending = true
        }
    }

    fun flush() {
        if (pending) {
            save()
        }
    }
}
