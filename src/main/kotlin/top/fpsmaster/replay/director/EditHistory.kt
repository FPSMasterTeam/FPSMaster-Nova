package top.fpsmaster.replay.director

/**
 * Snapshot undo/redo for an [EditProject]. Checkpoints are taken *before* a mutation.
 *
 * Snapshots are deep copies rather than serialised text: the project is a few dozen small objects,
 * and copying them is both cheaper and immune to the field-name drift a JSON round-trip would
 * silently swallow.
 */
class EditHistory {
    private val undoStack = ArrayDeque<EditProject>()
    private val redoStack = ArrayDeque<EditProject>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val depth: Int
        get() = undoStack.size

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun checkpoint(project: EditProject) {
        undoStack.addFirst(project.copy())
        redoStack.clear()
        while (undoStack.size > LIMIT) {
            undoStack.removeLast()
        }
    }

    /** The project as it was before the last checkpointed mutation, or null with nothing to undo. */
    fun undo(current: EditProject): EditProject? {
        val previous = undoStack.removeFirstOrNull() ?: return null
        redoStack.addFirst(current.copy())
        return previous
    }

    fun redo(current: EditProject): EditProject? {
        val next = redoStack.removeFirstOrNull() ?: return null
        undoStack.addFirst(current.copy())
        return next
    }

    companion object {
        const val LIMIT: Int = 40
    }
}
