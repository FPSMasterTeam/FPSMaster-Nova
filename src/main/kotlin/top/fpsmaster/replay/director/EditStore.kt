package top.fpsmaster.replay.director

import com.google.gson.GsonBuilder
import java.io.File

/**
 * Reads and writes edit projects as JSON sidecars. A project never rewrites the recording it
 * references, so a bad edit costs an edit and not a session.
 */
class EditStore(private val directory: File) {

    fun list(): List<String> = (directory.listFiles { file -> file.name.endsWith(EXTENSION) } ?: emptyArray())
        .map { it.name.removeSuffix(EXTENSION) }
        .sorted()

    fun fileFor(name: String): File = File(directory, sanitise(name) + EXTENSION)

    fun load(name: String): EditProject? {
        val file = fileFor(name)
        if (!file.isFile) {
            return null
        }
        return runCatching { GSON.fromJson(file.readText(), EditProject::class.java) }
            .getOrNull()
            ?.also { it.repair() }
    }

    fun save(project: EditProject) {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw java.io.IOException("could not create $directory")
        }
        project.updated = System.currentTimeMillis()
        val target = fileFor(project.name)
        // Write beside the target and move, so an interrupted save cannot leave half a project
        // where a whole one used to be.
        val temporary = File(target.parentFile, target.name + ".tmp")
        temporary.writeText(GSON.toJson(project))
        if (!temporary.renameTo(target)) {
            target.delete()
            if (!temporary.renameTo(target)) {
                throw java.io.IOException("could not replace $target")
            }
        }
    }

    fun delete(name: String): Boolean = fileFor(name).delete()

    companion object {
        const val EXTENSION: String = ".novaedit"

        private val GSON = GsonBuilder().setPrettyPrinting().create()

        fun sanitise(name: String): String =
            name.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
                .joinToString("")
                .ifEmpty { "project" }
    }
}

/**
 * Fills in what a hand-edited or older sidecar may be missing. Gson leaves absent fields null even
 * where Kotlin declares them non-null, so anything read from disk goes through here first.
 */
@Suppress("SENSELESS_COMPARISON")
private fun EditProject.repair() {
    if (clips == null) {
        clips = mutableListOf()
    }
    if (clips.isEmpty()) {
        clips.add(EditClip(0, 1))
    }
    if (camera == null) {
        camera = CameraTrack()
    }
    camera.repair()
    clips.forEach { clip ->
        if (clip.name == null) {
            clip.name = ""
        }
        if (clip.curve != null && clip.curve!!.size < 2) {
            clip.curve = null
        }
    }
}

@Suppress("SENSELESS_COMPARISON")
private fun CameraTrack.repair() {
    if (position == null) position = mutableListOf()
    if (yaw == null) yaw = mutableListOf()
    if (pitch == null) pitch = mutableListOf()
    if (roll == null) roll = mutableListOf()
    if (fov == null) fov = mutableListOf()
    CameraChannel.entries.forEach { channel ->
        val keys = channel(channel)
        keys.forEach {
            if (it.easing == null) it.easing = Easing.EASE_IN_OUT
            if (it.path == null) it.path = Transition.LINEAR
        }
        keys.sortBy { it.timeMillis }
    }
}
