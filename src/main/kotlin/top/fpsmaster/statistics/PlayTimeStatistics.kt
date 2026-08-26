package top.fpsmaster.statistics

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.fpsmaster.logger
import top.fpsmaster.mc
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How long this account has played, per context (a server address, or a single-player world).
 *
 * Three numbers are kept because they answer different questions: the current session, today, and
 * everything ever recorded for that context. Time is accumulated from wall-clock deltas while a world
 * is loaded, so pausing at the title screen does not count.
 */
object PlayTimeStatistics {
    const val MODE_SESSION = 0
    const val MODE_TODAY = 1
    const val MODE_TOTAL = 2

    private const val SAVE_INTERVAL_MS = 5_000L
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val entries = HashMap<String, Entry>()
    private var loaded = false
    private var dirty = false
    private var currentKey: String? = null
    private var lastTickMillis = 0L
    private var sessionMillis = 0L
    private var lastSaveMillis = 0L

    @Synchronized
    fun update() {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val key = resolveContextKey()

        if (key == null) {
            if (currentKey != null) {
                accumulate(now)
                currentKey = null
                sessionMillis = 0L
            }
            lastTickMillis = now
            flushIfDue(now)
            return
        }

        if (key != currentKey) {
            if (currentKey != null) {
                accumulate(now)
            }
            currentKey = key
            sessionMillis = 0L
            lastTickMillis = now
            return
        }

        val delta = now - lastTickMillis
        lastTickMillis = now
        // A large delta means the game was suspended (window minimised, machine asleep); ignore it.
        if (delta in 1..10_000) {
            sessionMillis += delta
            val entry = entries.getOrPut(key) { Entry() }
            entry.total += delta
            if (entry.day != dayKey()) {
                entry.day = dayKey()
                entry.today = 0L
            }
            entry.today += delta
            dirty = true
        }
        flushIfDue(now)
    }

    @Synchronized
    fun displayMillis(mode: Int): Long {
        ensureLoaded()
        val key = currentKey ?: return if (mode == MODE_SESSION) sessionMillis else 0L
        val entry = entries[key] ?: return sessionMillis
        return when (mode) {
            MODE_TODAY -> if (entry.day == dayKey()) entry.today else 0L
            MODE_TOTAL -> entry.total
            else -> sessionMillis
        }
    }

    @Synchronized
    fun flush() {
        if (dirty) {
            save()
        }
    }

    fun format(millis: Long): String {
        val totalSeconds = millis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    private fun accumulate(now: Long) {
        val delta = now - lastTickMillis
        lastTickMillis = now
        if (delta in 1..10_000) {
            currentKey?.let { key ->
                val entry = entries.getOrPut(key) { Entry() }
                entry.total += delta
                if (entry.day == dayKey()) entry.today += delta
                dirty = true
            }
        }
    }

    private fun flushIfDue(now: Long) {
        if (dirty && now - lastSaveMillis >= SAVE_INTERVAL_MS) {
            save()
            lastSaveMillis = now
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val file = file()
        if (!Files.isRegularFile(file)) return
        runCatching {
            val root = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
            root.entrySet().forEach { (key, element) ->
                if (!element.isJsonObject) return@forEach
                val json = element.asJsonObject
                entries[key] = Entry(
                    total = json.longOr("total"),
                    today = json.longOr("today"),
                    day = json.get("day")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                )
            }
        }.onFailure { logger.warn("Failed to read play time statistics: ${it.message}") }
    }

    private fun save() {
        dirty = false
        runCatching {
            val root = JsonObject()
            entries.forEach { (key, entry) ->
                root.add(key, JsonObject().apply {
                    addProperty("total", entry.total)
                    addProperty("today", entry.today)
                    addProperty("day", entry.day)
                })
            }
            val file = file()
            Files.createDirectories(file.parent)
            Files.newBufferedWriter(file).use { gson.toJson(root, it) }
        }.onFailure { logger.warn("Failed to write play time statistics: ${it.message}") }
    }

    /** Server address while multiplayer, world folder while singleplayer, null at the menus. */
    private fun resolveContextKey(): String? {
        if (mc.level == null) return null
        mc.currentServer?.ip?.takeIf { it.isNotBlank() }?.let { return "server:$it" }
        val singlePlayer = mc.singleplayerServer?.worldData?.levelName
        return if (singlePlayer.isNullOrBlank()) "local:world" else "local:$singlePlayer"
    }

    private fun file(): Path = mc.gameDirectory.toPath()
        .resolve("fpsmaster")
        .resolve("statistics")
        .resolve("play_time.json")

    private fun dayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    private fun JsonObject.longOr(name: String): Long =
        get(name)?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L

    private class Entry(var total: Long = 0L, var today: Long = 0L, var day: String = "")
}
