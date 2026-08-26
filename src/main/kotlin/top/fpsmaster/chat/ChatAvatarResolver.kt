package top.fpsmaster.chat

import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import top.fpsmaster.mc
import java.util.Locale

/**
 * Works out which player said a chat line, so a head can be drawn beside it.
 *
 * Servers format chat however they like — `[MVP+] Bob: hi`, `Guild > Bob: hi`, `<Bob> hi` — so there
 * is no reliable parse. Three signals are used, best first:
 *
 * 1. A whisper click-event on the line. Vanilla and most chat plugins attach `/msg <name> ` to the
 *    sender's name specifically, which names the sender exactly rather than guessing.
 * 2. A tab-list or display name appearing in the text before a chat delimiter (`:`, `>`, `»`).
 *    The rightmost such match wins, so a rank or guild prefix cannot outrank the real name.
 * 3. A plausible username token before the first delimiter, for players not on the tab list.
 *
 * A line with no delimiter at all is a system line (`Bob joined the game`), where the subject comes
 * first — those take the leftmost match instead, or "Bob was slain by Alice" would draw Alice.
 *
 * Nothing here touches the network: a name that the client cannot already resolve to a skin is
 * remembered as a miss instead of being looked up.
 */
object ChatAvatarResolver {
    private const val MAX_CACHE_SIZE = 256
    private const val SENDER_TTL_MILLIS = 1_000L
    private const val PLAYER_INDEX_TTL_MILLIS = 500L
    private const val MAX_SCANNED_CHARACTERS = 96
    private const val MAX_SENDER_SEPARATOR_DISTANCE = 16

    private val WHISPER_COMMAND_PREFIXES = arrayOf("/tell ", "/msg ", "/whisper ", "/w ", "/t ")
    private val TIMESTAMP_PREFIX =
        Regex("^(?:\\[\\d\\d:\\d\\d(?::\\d\\d)?(?: [AP]M)?]|<\\d\\d:\\d\\d>)\\s*")

    private class Sender(val name: String?, val expireAt: Long)

    private class Candidate(val realName: String, val matchName: String)

    private val senders = object : LinkedHashMap<String, Sender>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Sender>): Boolean =
            size > MAX_CACHE_SIZE
    }

    private var playerIndex: List<Candidate> = emptyList()
    private var playerIndexExpireAt = 0L

    /** The sender's account name, or null when the line cannot be attributed to a player. */
    fun senderOf(component: Component): String? {
        val text = ChatFormatting.stripFormatting(component.string) ?: component.string
        if (text.isBlank()) {
            return null
        }

        val key = text.take(MAX_SCANNED_CHARACTERS).lowercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        synchronized(senders) {
            senders[key]?.let { cached ->
                if (cached.expireAt > now) {
                    return cached.name
                }
            }
        }

        val name = whisperTarget(component)
            ?: onlinePlayerIn(text)
            ?: likelySenderName(text)
        synchronized(senders) {
            senders[key] = Sender(name, now + SENDER_TTL_MILLIS)
        }
        return name
    }

    fun playerInfo(name: String): PlayerInfo? = mc.connection?.onlinePlayers?.firstOrNull {
        it.profile?.name.equals(name, ignoreCase = true)
    }

    fun clear() {
        synchronized(senders) { senders.clear() }
        playerIndex = emptyList()
        playerIndexExpireAt = 0L
    }

    private fun whisperTarget(component: Component): String? {
        val target = whisperTargetOf(component.style.clickEvent)
        if (target != null) {
            return target
        }
        component.siblings.forEach { sibling ->
            whisperTarget(sibling)?.let { return it }
        }
        return null
    }

    private fun whisperTargetOf(clickEvent: ClickEvent?): String? {
        val command = when {
            clickEvent == null -> null
            //? if >=1.21.5 {
            clickEvent is ClickEvent.SuggestCommand -> clickEvent.command()
            clickEvent is ClickEvent.RunCommand -> clickEvent.command()
            //?} else {
            /*clickEvent.action == ClickEvent.Action.SUGGEST_COMMAND -> clickEvent.value
            clickEvent.action == ClickEvent.Action.RUN_COMMAND -> clickEvent.value
            *///?}
            else -> null
        }?.trim() ?: return null

        val prefix = WHISPER_COMMAND_PREFIXES.firstOrNull { command.startsWith(it, ignoreCase = true) }
            ?: return null
        val name = command.substring(prefix.length).trim().substringBefore(' ')
        return if (isValidPlayerName(name)) name else null
    }

    private fun onlinePlayerIn(text: String): String? {
        val candidates = onlinePlayers()
        if (candidates.isEmpty()) {
            return null
        }

        val head = stripTimestamp(text).take(MAX_SCANNED_CHARACTERS)
        bestMatch(head, candidates, requireDelimiter = true, preferRightmost = true)?.let { return it }
        return if (firstDelimiter(head) < 0) {
            bestMatch(head, candidates, requireDelimiter = false, preferRightmost = false)
        } else {
            null
        }
    }

    private fun bestMatch(
        text: String,
        candidates: List<Candidate>,
        requireDelimiter: Boolean,
        preferRightmost: Boolean
    ): String? {
        var best: Candidate? = null
        var bestIndex = -1
        var bestLength = -1
        candidates.forEach { candidate ->
            val index = indexOfName(text, candidate.matchName, requireDelimiter, preferRightmost)
            if (index >= 0) {
                val length = candidate.matchName.length
                val better = best == null ||
                    (if (preferRightmost) index > bestIndex else index < bestIndex) ||
                    (index == bestIndex && length > bestLength)
                if (better) {
                    best = candidate
                    bestIndex = index
                    bestLength = length
                }
            }
        }
        return best?.realName
    }

    /**
     * Account names and tab-list display names of everyone the client knows about. Rebuilt on a short
     * timer rather than per line, since a full chat page re-resolves every line each frame.
     */
    private fun onlinePlayers(): List<Candidate> {
        val now = System.currentTimeMillis()
        if (now < playerIndexExpireAt) {
            return playerIndex
        }

        val candidates = ArrayList<Candidate>()
        runCatching {
            mc.connection?.onlinePlayers?.forEach { info ->
                val realName = info.profile?.name ?: return@forEach
                addCandidate(candidates, realName, realName)
                info.tabListDisplayName?.let { display ->
                    addCandidate(candidates, realName, ChatFormatting.stripFormatting(display.string) ?: display.string)
                }
            }
        }
        playerIndex = candidates
        playerIndexExpireAt = now + PLAYER_INDEX_TTL_MILLIS
        return candidates
    }

    private fun addCandidate(candidates: MutableList<Candidate>, realName: String, matchName: String?) {
        val trimmed = matchName?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return
        }
        if (candidates.none { it.realName.equals(realName, true) && it.matchName.equals(trimmed, true) }) {
            candidates.add(Candidate(realName, trimmed))
        }
    }

    private fun indexOfName(
        text: String,
        name: String,
        requireDelimiter: Boolean,
        preferRightmost: Boolean
    ): Int {
        val lowerText = text.lowercase(Locale.ROOT)
        val lowerName = name.lowercase(Locale.ROOT)
        var best = -1
        var from = 0
        while (from < lowerText.length) {
            val index = lowerText.indexOf(lowerName, from)
            if (index < 0) {
                break
            }
            val after = index + lowerName.length
            // Word boundaries on both sides: "tom" must not match inside "custom" or "tomato".
            val boundedBefore = index == 0 || !isWordChar(lowerText[index - 1])
            val boundedAfter = after >= lowerText.length || !isWordChar(lowerText[after])
            if (boundedBefore && boundedAfter && (!requireDelimiter || hasDelimiterAfter(text, after))) {
                best = index
                if (!preferRightmost) {
                    return best
                }
            }
            from = index + 1
        }
        return best
    }

    private fun hasDelimiterAfter(text: String, from: Int): Boolean {
        val limit = minOf(text.length, from + MAX_SENDER_SEPARATOR_DISTANCE)
        for (i in from until limit) {
            if (isChatDelimiter(text[i])) {
                return true
            }
        }
        return false
    }

    /**
     * Drops a leading clock stamp another chat mod may have prepended, e.g. `[12:34] `. Without this the
     * name is no longer at the head of the line and the delimiter-anchored match degrades.
     */
    private fun stripTimestamp(text: String): String {
        val match = TIMESTAMP_PREFIX.find(text) ?: return text
        return text.substring(match.range.last + 1)
    }

    private fun likelySenderName(text: String): String? {
        val delimiter = firstDelimiter(text)
        if (delimiter < 0) {
            return null
        }
        return text.take(minOf(delimiter, MAX_SCANNED_CHARACTERS))
            .split(Regex("[^A-Za-z0-9_]+"))
            .lastOrNull { isValidPlayerName(it) }
    }

    private fun firstDelimiter(text: String): Int = text.indexOfFirst { isChatDelimiter(it) }

    private fun isChatDelimiter(character: Char): Boolean =
        character == ':' || character == '：' || character == '>' || character == '»' || character == '›'

    /**
     * Word-boundary alphabet. Unlike a username's ASCII alphabet this is Unicode-aware, so a CJK display
     * name sitting flush against other CJK text is not mistaken for a standalone token.
     */
    private fun isWordChar(character: Char): Boolean = character.isLetterOrDigit() || character == '_'

    private fun isValidPlayerName(name: String): Boolean =
        name.length in 3..16 && name.all { it.isAsciiLetterOrDigit() || it == '_' }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
