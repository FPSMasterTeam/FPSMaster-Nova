package top.fpsmaster.config

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfigBackupsTest {
    private var now = 1_000_000L
    private val root = Files.createTempDirectory("nova-backups")
    private val backups = ConfigBackups(root, keep = 3, minIntervalMs = 60_000L, clock = { now })

    @Test
    fun `unchanged content and saves inside the interval take no copy`() {
        assertNotNull(backups.snapshot("default", "a"))
        now += 1_000
        assertNull(backups.snapshot("default", "b"), "inside the interval")
        now += 60_000
        assertNotNull(backups.snapshot("default", "b"))
        now += 60_000
        assertNull(backups.snapshot("default", "b"), "same as the newest copy")
        assertEquals(2, backups.list("default").size)
    }

    @Test
    fun `forced snapshots ignore the interval and old copies are pruned`() {
        repeat(5) { index ->
            now += 1_000
            backups.snapshot("default", "v$index", force = true)
        }
        val kept = backups.list("default")
        assertEquals(3, kept.size)
        assertEquals("v4", kept.first().readText(), "newest first")
    }

    @Test
    fun `rollback target is the newest copy that differs from the current file`() {
        backups.snapshot("default", "old", force = true)
        now += 1_000
        backups.snapshot("default", "current", force = true)

        assertEquals("old", backups.latestDifferentFrom("default", "current")?.readText())
        assertNull(backups.latestDifferentFrom("missing", "x"))
    }

    @Test
    fun `atomic write replaces the whole file`() {
        val target = root.resolve("file.json")
        AtomicFiles.write(target, "first")
        AtomicFiles.write(target, "second", ownerOnly = true)
        assertEquals("second", target.readText())
        assertEquals(listOf("file.json"), Files.list(root).use { files -> files.map { it.fileName.toString() }.toList() })
    }
}
