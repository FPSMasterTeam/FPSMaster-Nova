package top.fpsmaster.replay

/**
 * The last few minutes of a session, held in memory as segments so that "save the last N minutes"
 * yields a recording that opens like any other.
 *
 * A packet stream cannot start at an arbitrary point: the world it describes was built by packets
 * that have since been evicted. So every segment opens with a keyframe — the session packets and a
 * terrain snapshot, exactly what [ReplayRecorder] writes when a recording starts mid-session — and a
 * clip always begins at a segment boundary. The oldest segment is only dropped once the next one has
 * a complete keyframe, so there is always a playable start.
 *
 * Memory is bounded twice: by time (segments wholly older than the window go) and by [maxBytes]
 * (oldest segments go first, even inside the window). Called from the network thread (packets) and
 * the client thread (samples, keyframes, saving); every method takes the lock.
 */
class ClipRing(private val maxBytes: Long) {

    class Segment internal constructor(val startMillis: Int) {
        internal val keyframe = ArrayList<ByteArray>()
        internal val records = ArrayList<ReplayRecord>()
        internal var bytes = 0L

        /** False while the terrain snapshot is still being taken over several ticks. */
        var keyframeDone = false
            internal set
    }

    /** What a save writes: the first segment's keyframe at time 0, then every record, rebased. */
    class Clip(val baseMillis: Int, val keyframe: List<ByteArray>, val records: List<ReplayRecord>) {
        val durationMillis: Int
            get() = records.lastOrNull()?.let { it.millis - baseMillis }?.coerceAtLeast(0) ?: 0
    }

    private val segments = ArrayDeque<Segment>()

    var totalBytes: Long = 0L
        private set

    val segmentCount: Int
        @Synchronized get() = segments.size

    /** Whether the newest segment alone has used up the budget, so a new keyframe is due early. */
    val newestOverBudget: Boolean
        @Synchronized get() = (segments.lastOrNull()?.bytes ?: 0L) * 2 > maxBytes

    /** Starts a segment whose keyframe begins with [bootstrap]; terrain follows via [addKeyframe]. */
    @Synchronized
    fun beginSegment(millis: Int, bootstrap: Collection<ByteArray>): Segment {
        val segment = Segment(millis)
        bootstrap.forEach { add(segment, it) }
        segments.addLast(segment)
        return segment
    }

    @Synchronized
    fun addKeyframe(segment: Segment, payload: ByteArray) {
        if (segments.contains(segment)) {
            add(segment, payload)
        }
    }

    @Synchronized
    fun finishKeyframe(segment: Segment) {
        segment.keyframeDone = true
    }

    /** Appends to the newest segment. Dropped when no segment has started yet. */
    @Synchronized
    fun add(record: ReplayRecord) {
        val segment = segments.lastOrNull() ?: return
        segment.records.add(record)
        val size = (record.payload?.size ?: 0) + RECORD_OVERHEAD
        segment.bytes += size
        totalBytes += size
    }

    /** Drops segments that fell out of [windowMillis] or over the byte budget. */
    @Synchronized
    fun trim(nowMillis: Int, windowMillis: Int) {
        while (segments.size > 1) {
            val next = segments.elementAt(1)
            if (!next.keyframeDone) {
                return
            }
            val outOfWindow = next.startMillis <= nowMillis - windowMillis
            if (!outOfWindow && totalBytes <= maxBytes) {
                return
            }
            totalBytes -= segments.removeFirst().bytes
        }
    }

    /** A playable copy of what is buffered, or null when no segment has a complete keyframe yet. */
    @Synchronized
    fun clip(): Clip? {
        val first = segments.firstOrNull { it.keyframeDone } ?: return null
        val records = ArrayList<ReplayRecord>()
        var include = false
        for (segment in segments) {
            if (segment === first) {
                include = true
            }
            if (include) {
                records.addAll(segment.records)
            }
        }
        return Clip(first.startMillis, ArrayList(first.keyframe), records)
    }

    @Synchronized
    fun clear() {
        segments.clear()
        totalBytes = 0L
    }

    private fun add(segment: Segment, payload: ByteArray) {
        segment.keyframe.add(payload)
        val size = payload.size + RECORD_OVERHEAD
        segment.bytes += size
        totalBytes += size
    }

    companion object {
        /** Rough per-record cost on the heap beyond the payload: object header, fields, list slot. */
        const val RECORD_OVERHEAD: Int = 64

        /** Re-stamps [record] relative to a clip that starts at [baseMillis]. */
        fun rebase(record: ReplayRecord, baseMillis: Int): ReplayRecord =
            record.withMillis((record.millis - baseMillis).coerceAtLeast(0))
    }
}
