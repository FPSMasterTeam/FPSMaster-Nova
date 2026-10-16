package top.fpsmaster.config

import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Rolling copies of each profile under `<root>/<profile>/<timestamp>.json`, newest last by name.
 *
 * Saves happen on every toggle and slider drag, so a copy is only taken when the content changed
 * and at most once per [minIntervalMs] (or when forced: the known-good file at startup, the state
 * just before a rollback). The oldest copies beyond [keep] are deleted.
 */
class ConfigBackups(
    private val root: Path,
    private val keep: Int = 10,
    private val minIntervalMs: Long = 10 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastTaken = HashMap<String, Long>()

    /** Returns the new backup, or null when none was needed. */
    @Synchronized
    fun snapshot(profile: String, content: String, force: Boolean = false): Path? {
        val now = clock()
        if (!force && now - (lastTaken[profile] ?: Long.MIN_VALUE / 2) < minIntervalMs) {
            return null
        }
        val newest = list(profile).firstOrNull()
        if (newest != null && runCatching { newest.readText() }.getOrNull() == content) {
            lastTaken[profile] = now
            return null
        }
        val directory = root.resolve(profile)
        var target = directory.resolve("${stamp(now)}.json")
        var suffix = 1
        while (target.exists()) {
            target = directory.resolve("${stamp(now)}-${suffix++}.json")
        }
        AtomicFiles.write(target, content)
        lastTaken[profile] = now
        list(profile).drop(keep).forEach { Files.deleteIfExists(it) }
        return target
    }

    /** Newest first. */
    @Synchronized
    fun list(profile: String): List<Path> {
        val directory = root.resolve(profile)
        if (!directory.isDirectory()) {
            return emptyList()
        }
        return directory.listDirectoryEntries("*.json").sortedByDescending { it.name }
    }

    /** The newest backup whose content differs from [current]: what "roll back" should restore. */
    @Synchronized
    fun latestDifferentFrom(profile: String, current: String?): Path? =
        list(profile).firstOrNull { runCatching { it.readText() }.getOrNull() != current }

    companion object {
        private fun stamp(millis: Long): String =
            SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date(millis))

        /** "2026-10-16 12:30" for a backup file name produced by [snapshot]. */
        fun label(backup: Path): String {
            val raw = backup.fileName.toString().removeSuffix(".json")
            return runCatching {
                val date = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).parse(raw.take(19))
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(date)
            }.getOrDefault(raw)
        }
    }
}
