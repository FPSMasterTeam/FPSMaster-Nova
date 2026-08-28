package top.fpsmaster.replay.director

import kotlin.math.abs

/**
 * Camera animation: each [CameraChannel] keeps its own keyframe list and easing, so a pan can be
 * keyed without touching the dolly. A channel with no keys holds the fallback pose — the live
 * camera while flying.
 *
 * Keyframes are addressed in *source* (replay) time, so slowing a clip slows the camera move
 * through it equally and the shot stays glued to the moment it frames.
 */
class CameraTrack {
    var position: MutableList<PropKeyframe> = mutableListOf()
    var yaw: MutableList<PropKeyframe> = mutableListOf()
    var pitch: MutableList<PropKeyframe> = mutableListOf()
    var roll: MutableList<PropKeyframe> = mutableListOf()
    var fov: MutableList<PropKeyframe> = mutableListOf()

    fun channel(channel: CameraChannel): MutableList<PropKeyframe> = when (channel) {
        CameraChannel.POSITION -> position
        CameraChannel.YAW -> yaw
        CameraChannel.PITCH -> pitch
        CameraChannel.ROLL -> roll
        CameraChannel.FOV -> fov
    }

    fun isEmpty(): Boolean = CameraChannel.entries.all { channel(it).isEmpty() }

    fun drivesPosition(): Boolean = position.isNotEmpty()

    fun drivesLook(): Boolean = yaw.isNotEmpty() || pitch.isNotEmpty()

    fun startMillis(): Int = CameraChannel.entries
        .mapNotNull { channel(it).firstOrNull()?.timeMillis }
        .minOrNull() ?: 0

    fun endMillis(): Int = CameraChannel.entries
        .mapNotNull { channel(it).lastOrNull()?.timeMillis }
        .maxOrNull() ?: 0

    /** Keys every channel at [timeMillis] from [pose]. */
    fun addPose(timeMillis: Int, pose: CameraPose, mergeWindowMillis: Int = MERGE_WINDOW_MILLIS) {
        CameraChannel.entries.forEach { add(it, timeMillis, pose, mergeWindowMillis) }
    }

    fun add(
        channel: CameraChannel,
        timeMillis: Int,
        pose: CameraPose,
        mergeWindowMillis: Int = MERGE_WINDOW_MILLIS,
    ): PropKeyframe {
        val values = when (channel) {
            CameraChannel.POSITION -> floatArrayOf(pose.x.toFloat(), pose.y.toFloat(), pose.z.toFloat())
            CameraChannel.YAW -> floatArrayOf(pose.yaw)
            CameraChannel.PITCH -> floatArrayOf(pose.pitch)
            CameraChannel.ROLL -> floatArrayOf(pose.roll)
            CameraChannel.FOV -> floatArrayOf(if (pose.fov <= 0f) DEFAULT_FOV else pose.fov)
        }
        return addValues(channel, timeMillis, values, mergeWindowMillis)
    }

    fun addValues(
        channel: CameraChannel,
        timeMillis: Int,
        values: FloatArray,
        mergeWindowMillis: Int = MERGE_WINDOW_MILLIS,
    ): PropKeyframe {
        val keys = channel(channel)
        val existing = keys.firstOrNull { abs(it.timeMillis - timeMillis) <= mergeWindowMillis }
        if (existing != null) {
            existing.a = values[0]
            if (channel.components > 1) {
                existing.b = values[1]
                existing.c = values[2]
            }
            return existing
        }
        val key = if (channel.components > 1) {
            PropKeyframe(timeMillis, values[0], values[1], values[2])
        } else {
            PropKeyframe(timeMillis, values[0])
        }
        if (channel == CameraChannel.POSITION) {
            key.path = Transition.SMOOTH
        }
        keys.add(key)
        keys.sortBy { it.timeMillis }
        return key
    }

    fun remove(channel: CameraChannel, key: PropKeyframe): Boolean = channel(channel).remove(key)

    fun nearest(channel: CameraChannel, timeMillis: Int, window: Int): PropKeyframe? =
        channel(channel)
            .filter { abs(it.timeMillis - timeMillis) <= window }
            .minByOrNull { abs(it.timeMillis - timeMillis) }

    fun clear() = CameraChannel.entries.forEach { channel(it).clear() }

    /**
     * Pose at [timeMillis]. Channels with no keys take [hold]; null only when every channel is empty
     * and no fallback was given.
     *
     * FOV is the one channel that does not fall back to [hold]: it comes out 0, meaning "the
     * timeline has nothing to say about the FOV". Holding it at the live value instead would look
     * identical on screen for one frame and then freeze — the adapter reads any positive FOV as the
     * timeline driving it, so the moment a shot has *any* keyframe the player's zoom stops working
     * and "key camera here" files the frozen number back into the project.
     */
    fun sample(timeMillis: Int, hold: CameraPose? = null): CameraPose? {
        if (isEmpty()) {
            return hold
        }
        val base = hold ?: CameraPose()
        val holdFov = if (base.fov <= 0f) DEFAULT_FOV else base.fov
        val pos = interpolateVec3(position, timeMillis, base.x.toFloat(), base.y.toFloat(), base.z.toFloat())
        return CameraPose(
            x = pos[0].toDouble(),
            y = pos[1].toDouble(),
            z = pos[2].toDouble(),
            yaw = interpolateScalar(yaw, timeMillis, base.yaw, angular = true),
            pitch = interpolateScalar(pitch, timeMillis, base.pitch, angular = false),
            fov = if (this.fov.isEmpty()) 0f else interpolateScalar(this.fov, timeMillis, holdFov, angular = false),
            roll = interpolateScalar(roll, timeMillis, base.roll, angular = true),
        )
    }

    fun copy(): CameraTrack = CameraTrack().also { copy ->
        CameraChannel.entries.forEach { channel ->
            channel(channel).mapTo(copy.channel(channel)) { it.copy() }
        }
    }

    companion object {
        const val DEFAULT_FOV: Float = 70f

        /** A key dropped this close to an existing one on the same channel replaces it. */
        const val MERGE_WINDOW_MILLIS: Int = 120

        private fun interpolateVec3(
            keys: List<PropKeyframe>,
            time: Int,
            hx: Float,
            hy: Float,
            hz: Float,
        ): FloatArray {
            if (keys.isEmpty()) {
                return floatArrayOf(hx, hy, hz)
            }
            val first = keys.first()
            if (time <= first.timeMillis) {
                return floatArrayOf(first.a, first.b, first.c)
            }
            val last = keys.last()
            if (time >= last.timeMillis) {
                return floatArrayOf(last.a, last.b, last.c)
            }
            val index = indexBefore(keys, time)
            val from = keys[index]
            val to = keys[index + 1]
            if (from.path == Transition.CUT) {
                return floatArrayOf(from.a, from.b, from.c)
            }
            val t = easedT(from, to, time)
            if (from.path == Transition.SMOOTH) {
                val before = if (index > 0) keys[index - 1] else from
                val after = if (index + 2 < keys.size) keys[index + 2] else to
                return floatArrayOf(
                    catmullRom(before.a, from.a, to.a, after.a, t),
                    catmullRom(before.b, from.b, to.b, after.b, t),
                    catmullRom(before.c, from.c, to.c, after.c, t),
                )
            }
            return floatArrayOf(
                from.a + (to.a - from.a) * t,
                from.b + (to.b - from.b) * t,
                from.c + (to.c - from.c) * t,
            )
        }

        private fun interpolateScalar(
            keys: List<PropKeyframe>,
            time: Int,
            hold: Float,
            angular: Boolean,
        ): Float {
            if (keys.isEmpty()) {
                return hold
            }
            val first = keys.first()
            if (time <= first.timeMillis) {
                return first.a
            }
            val last = keys.last()
            if (time >= last.timeMillis) {
                return last.a
            }
            val index = indexBefore(keys, time)
            val from = keys[index]
            val to = keys[index + 1]
            if (from.path == Transition.CUT) {
                return from.a
            }
            val delta = if (angular) shortestArc(to.a - from.a) else to.a - from.a
            return from.a + delta * easedT(from, to, time)
        }

        private fun indexBefore(keys: List<PropKeyframe>, time: Int): Int {
            var index = 0
            while (index < keys.size - 1 && keys[index + 1].timeMillis <= time) {
                index++
            }
            return index
        }

        private fun easedT(from: PropKeyframe, to: PropKeyframe, time: Int): Float {
            val span = (to.timeMillis - from.timeMillis).toFloat()
            val linear = if (span <= 0f) 1f else (time - from.timeMillis) / span
            return ease(from.easing, linear)
        }

        fun ease(easing: Easing, t: Float): Float = when (easing) {
            Easing.LINEAR -> t
            Easing.EASE -> cubicBezier(t, 0.25f, 0.1f, 0.25f, 1f)
            Easing.EASE_IN -> cubicBezier(t, 0.42f, 0f, 1f, 1f)
            Easing.EASE_OUT -> cubicBezier(t, 0f, 0f, 0.58f, 1f)
            Easing.EASE_IN_OUT -> cubicBezier(t, 0.42f, 0f, 0.58f, 1f)
        }

        /** CSS timing function: solves x(guess) = t by Newton, then evaluates y. */
        private fun cubicBezier(t: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val x = t.coerceIn(0f, 1f)
            var guess = x
            repeat(6) {
                val u = 1f - guess
                val bx = 3f * u * u * guess * x1 + 3f * u * guess * guess * x2 + guess * guess * guess
                val dx = 3f * u * u * x1 + 6f * u * guess * (x2 - x1) + 3f * guess * guess * (1f - x2)
                if (abs(dx) < 1e-6f) {
                    return@repeat
                }
                guess = (guess - (bx - x) / dx).coerceIn(0f, 1f)
            }
            val u = 1f - guess
            return 3f * u * u * guess * y1 + 3f * u * guess * guess * y2 + guess * guess * guess
        }

        private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
            val t2 = t * t
            val t3 = t2 * t
            return 0.5f * (
                2f * p1 +
                    (-p0 + p2) * t +
                    (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                    (-p0 + 3f * p1 - 3f * p2 + p3) * t3
                )
        }

        /** Turns a yaw delta into the shorter way round, so 350 -> 10 sweeps 20 and not -340. */
        fun shortestArc(deltaYaw: Float): Float {
            var wrapped = deltaYaw % 360f
            if (wrapped >= 180f) {
                wrapped -= 360f
            }
            if (wrapped < -180f) {
                wrapped += 360f
            }
            return wrapped
        }
    }
}
