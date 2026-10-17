package top.fpsmaster.replay

import java.io.Closeable

/**
 * Where playback stands in a recording, and the work of getting it somewhere else.
 *
 * A packet stream only runs forwards: any packet may be the one that placed the block on screen, so
 * the only way back is to tear the world down ([rebuild]) and apply everything again from the start.
 * What this changes over holding the whole recording in memory is *when* that work happens: a seek
 * only moves [targetMillis], and [pump] applies records towards it within a time budget, a slice
 * per tick. The records come from a [ReplayStream], so memory holds a bounded window ahead of the
 * playhead rather than the recording.
 *
 * Minecraft-free on purpose, so seeking can be tested against a synthetic recording.
 */
class ReplayCursor(
    private val openStream: () -> ReplayStream,
    private val apply: (ReplayRecord) -> Unit,
    private val rebuild: () -> Unit,
) : Closeable {

    private var stream: ReplayStream = openStream()

    /** Where playback has been asked to be. */
    var targetMillis: Int = 0
        private set

    /** The newest record applied so far; -1 before anything, even the records at zero, has been. */
    var appliedMillis: Int = -1
        private set

    /** Every record stamped at or before this has been applied. Trails [targetMillis] while loading. */
    var reachedMillis: Int = -1
        private set

    /** Times the world had to be rebuilt from the start. */
    var rebuilds: Int = 0
        private set

    val isCaughtUp: Boolean
        get() = reachedMillis >= targetMillis

    /** Caught up with nothing left to read: playback has reached the end of the recording. */
    val isAtEnd: Boolean
        get() = isCaughtUp && stream.isExhausted

    /**
     * Moves the target. Behind anything already applied, that means rebuilding the world and
     * reading again from the beginning; anywhere else it costs nothing until [pump].
     */
    fun seekTo(millis: Int) {
        val target = millis.coerceAtLeast(0)
        if (target < appliedMillis) {
            stream.close()
            rebuild()
            stream = openStream()
            appliedMillis = -1
            reachedMillis = -1
            rebuilds++
        }
        targetMillis = target
    }

    /**
     * Applies records towards [targetMillis] until caught up, until the reader has nothing ready,
     * or until [deadlineNanos] passes — whichever comes first. Always applies at least one ready
     * record, so a tiny budget still makes progress. Returns whether it caught up.
     */
    fun pump(deadlineNanos: Long, clock: () -> Long = System::nanoTime): Boolean {
        while (reachedMillis < targetMillis) {
            // Read "finished" before looking at the buffer: the reader adds its last record and then
            // finishes, so finished-then-empty really is the end.
            val finished = stream.isFinished
            val next = stream.peek()
            if (next == null) {
                if (finished) {
                    reachedMillis = targetMillis
                }
                break
            }
            if (next.millis > targetMillis) {
                reachedMillis = targetMillis
                break
            }
            stream.poll()
            apply(next)
            appliedMillis = maxOf(appliedMillis, next.millis)
            if (clock() - deadlineNanos >= 0) {
                break
            }
        }
        return isCaughtUp
    }

    override fun close() = stream.close()
}
