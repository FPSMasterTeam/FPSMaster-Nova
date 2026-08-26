package top.fpsmaster.replay.director

/**
 * A saved edit that *references* a recording rather than rewriting it.
 *
 * [source] is the recording's basename (`foo` for `foo.novareplay`). [clips] is the output edit
 * list. [camera] holds keyframes in source time, so a slow clip also slows the camera move through
 * it.
 */
class EditProject {
    var name: String = ""
    var source: String = ""
    var updated: Long = 0L
    var clips: MutableList<EditClip> = mutableListOf()
    var camera: CameraTrack = CameraTrack()

    /** True until the first split, trim or reorder — while it holds, the single clip may grow. */
    var pristine: Boolean = true

    /** The recording is still being read when a project is opened; let the lone clip follow it. */
    fun ensureDuration(sourceDuration: Int) {
        if (!pristine || clips.size != 1 || sourceDuration <= 0) {
            return
        }
        val clip = clips[0]
        if (clip.srcIn == 0 && clip.speed == 1f && clip.srcOut < sourceDuration) {
            clip.srcOut = sourceDuration
        }
    }

    fun outputDurationMillis(): Long = clips.sumOf { it.outputLength() }

    fun outputStartOf(index: Int): Long {
        var acc = 0L
        for (i in 0 until minOf(index, clips.size)) {
            acc += clips[i].outputLength()
        }
        return acc
    }

    fun clipIndexAtOutput(outputMillis: Long): Int {
        if (clips.isEmpty()) {
            return 0
        }
        var acc = 0L
        for (i in clips.indices) {
            val length = clips[i].outputLength()
            if (outputMillis < acc + length || i == clips.size - 1) {
                return i
            }
            acc += length
        }
        return clips.size - 1
    }

    /** Where in the recording the movie is at [outputMillis]. */
    fun mapOutputToSource(outputMillis: Long): Int {
        if (clips.isEmpty()) {
            return maxOf(0L, outputMillis).toInt()
        }
        var acc = 0L
        for (clip in clips) {
            val length = clip.outputLength()
            if (length <= 0) {
                continue
            }
            if (outputMillis < acc + length) {
                return clip.srcIn + clip.sourceOffsetForOutput(outputMillis - acc)
            }
            acc += length
        }
        return clips.last().srcOut
    }

    fun outputTimeFor(clipIndex: Int, sourceMillis: Int): Long {
        val acc = outputStartOf(clipIndex)
        if (clipIndex < 0 || clipIndex >= clips.size) {
            return acc
        }
        val clip = clips[clipIndex]
        val local = (sourceMillis - clip.srcIn).coerceIn(0, clip.sourceLength())
        return acc + clip.outputOffsetForSource(local)
    }

    /**
     * Cuts the clip under [outputMillis] in two. Refuses a cut that would leave either half shorter
     * than [MIN_CLIP_SOURCE]: a clip below that carries no usable packets and only clutters the
     * timeline.
     */
    fun splitAtOutput(outputMillis: Long): Boolean {
        if (clips.isEmpty()) {
            return false
        }
        val index = clipIndexAtOutput(outputMillis)
        val clip = clips[index]
        val local = outputMillis - outputStartOf(index)
        if (local < MIN_CLIP_OUTPUT || local > clip.outputLength() - MIN_CLIP_OUTPUT) {
            return false
        }
        val srcCut = clip.srcIn + clip.sourceOffsetForOutput(local)
        if (srcCut <= clip.srcIn + MIN_CLIP_SOURCE || srcCut >= clip.srcOut - MIN_CLIP_SOURCE) {
            return false
        }
        val pCut = if (clip.sourceLength() <= 0) 0.5f else (srcCut - clip.srcIn) / clip.sourceLength().toFloat()
        val tail = clip.copy()
        tail.srcIn = srcCut
        clip.srcOut = srcCut
        clip.splitCurveAt(pCut, tail)
        clips.add(index + 1, tail)
        pristine = false
        return true
    }

    /** The timeline always keeps at least one clip; there is nothing to edit without one. */
    fun removeClip(index: Int): Boolean {
        if (index < 0 || index >= clips.size || clips.size <= 1) {
            return false
        }
        clips.removeAt(index)
        pristine = false
        return true
    }

    fun moveClip(from: Int, to: Int): Boolean {
        if (from < 0 || from >= clips.size || to < 0 || to >= clips.size || from == to) {
            return false
        }
        clips.add(to, clips.removeAt(from))
        pristine = false
        return true
    }

    fun setSpeed(index: Int, speed: Float) {
        val clip = clips.getOrNull(index) ?: return
        clip.speed = if (speed <= 0f) 1f else speed
        clip.clearCurve()
        pristine = false
    }

    fun toggleCurve(index: Int) {
        val clip = clips.getOrNull(index) ?: return
        if (clip.hasCurve()) {
            clip.speed = clip.speedAt(0.5f)
            clip.clearCurve()
        } else {
            clip.enableCurve()
        }
        pristine = false
    }

    /** Inserts a copy of clip [index] immediately after it, same source range. */
    fun duplicateClip(index: Int): Int {
        val clip = clips.getOrNull(index) ?: return -1
        clips.add(index + 1, clip.copy())
        pristine = false
        return index + 1
    }

    fun trimSource(index: Int, newIn: Int, newOut: Int) {
        val clip = clips.getOrNull(index) ?: return
        val start = maxOf(0, newIn)
        clip.srcIn = start
        clip.srcOut = maxOf(start + MIN_CLIP_SOURCE, newOut)
        pristine = false
    }

    fun copy(): EditProject = EditProject().also { copy ->
        copy.name = name
        copy.source = source
        copy.updated = updated
        copy.pristine = pristine
        clips.mapTo(copy.clips) { it.copy() }
        copy.camera = camera.copy()
    }

    companion object {
        /** Shortest source range a clip may hold. */
        const val MIN_CLIP_SOURCE: Int = 200

        /** Shortest output distance from a clip edge a split may land. */
        const val MIN_CLIP_OUTPUT: Long = 80L

        fun create(name: String, source: String, sourceDuration: Int): EditProject = EditProject().also {
            it.name = name
            it.source = source
            it.updated = System.currentTimeMillis()
            it.pristine = true
            it.clips.add(EditClip(0, maxOf(1, sourceDuration)))
        }
    }
}
