package top.fpsmaster.replay.director

import kotlin.math.abs

/**
 * One knot on a clip's speed curve. [p] is 0..1 along the clip's source range, [s] the playback rate
 * there. The handles are offsets in the same `(p, s)` space, so the segment between two knots is a
 * cubic Bézier that can be dragged like an After Effects speed graph.
 */
class SpeedPoint(
    @JvmField var p: Float = 0f,
    @JvmField var s: Float = 1f,
) {
    @JvmField
    var inDx: Float = -0.08f

    @JvmField
    var inDy: Float = 0f

    @JvmField
    var outDx: Float = 0.08f

    @JvmField
    var outDy: Float = 0f

    fun copy(): SpeedPoint = SpeedPoint(p, s).also {
        it.inDx = inDx
        it.inDy = inDy
        it.outDx = outDx
        it.outDy = outDy
    }
}

/**
 * One clip on the timeline: a range of the source replay, played at a speed.
 *
 * Clips are ordered in *output* time, so the same source moment can appear twice and later source
 * can play before earlier source.
 *
 * [curve] is optional. Without it the clip plays at the constant [speed]; with it the rate varies
 * along the source range and the output length is the integral of `d(source) / speed`.
 */
class EditClip(
    @JvmField var srcIn: Int = 0,
    @JvmField var srcOut: Int = 0,
) {
    @JvmField
    var name: String = ""

    @JvmField
    var speed: Float = 1f

    @JvmField
    var curve: MutableList<SpeedPoint>? = null

    fun copy(): EditClip = EditClip(srcIn, srcOut).also { copy ->
        copy.name = name
        copy.speed = speed
        copy.curve = curve?.mapTo(mutableListOf()) { it.copy() }
    }

    fun sourceLength(): Int = (srcOut - srcIn).coerceAtLeast(0)

    fun clampedSpeed(): Float = clampSpeed(speed)

    fun hasCurve(): Boolean = (curve?.size ?: 0) >= 2

    fun clearCurve() {
        curve = null
    }

    fun enableCurve() {
        if (hasCurve()) {
            return
        }
        val rate = clampedSpeed()
        val start = SpeedPoint(0f, rate).also { it.outDx = 0.33f; it.outDy = 0f }
        val end = SpeedPoint(1f, rate).also { it.inDx = -0.33f; it.inDy = 0f }
        curve = mutableListOf(start, end)
    }

    fun sortCurve() {
        val knots = curve ?: return
        knots.sortBy { it.p }
        if (knots.isNotEmpty()) {
            knots.first().p = 0f
            knots.last().p = 1f
        }
    }

    fun addCurvePoint(p: Float, s: Float): SpeedPoint {
        enableCurve()
        val knots = curve!!
        val at = p.coerceIn(0f, 1f)
        val rate = clampSpeed(s)
        knots.firstOrNull { abs(it.p - at) < 0.03f }?.let {
            it.s = rate
            return it
        }
        val point = SpeedPoint(at, rate)
        knots.add(point)
        sortCurve()
        return point
    }

    /** Removes an interior knot. The two endpoints define the curve and cannot be dropped. */
    fun removeCurvePoint(index: Int) {
        val knots = curve ?: return
        if (index <= 0 || index >= knots.size - 1) {
            return
        }
        knots.removeAt(index)
        if (knots.size < 2) {
            curve = null
        }
    }

    /** Instantaneous rate at a source millisecond. */
    fun speedAtSource(sourceMillis: Int): Float {
        val length = sourceLength()
        if (length <= 0) {
            return clampedSpeed()
        }
        return speedAt((sourceMillis - srcIn) / length.toFloat())
    }

    fun speedAt(u: Float): Float {
        val at = u.coerceIn(0f, 1f)
        val knots = curve
        if (knots == null || knots.size < 2) {
            return clampedSpeed()
        }
        var i = 0
        while (i < knots.size - 1 && knots[i + 1].p < at) {
            i++
        }
        val a = knots[i]
        val b = knots[minOf(i + 1, knots.size - 1)]
        if (b.p <= a.p + 1e-5f) {
            return clampSpeed(a.s)
        }
        val t = tForX(at, a.p, a.p + a.outDx, b.p + b.inDx, b.p)
        return clampSpeed(bezier(t, a.s, a.s + a.outDy, b.s + b.inDy, b.s))
    }

    /** Length this clip occupies in the output, after the speed stretch. */
    fun outputLength(): Long {
        val length = sourceLength()
        if (length <= 0) {
            return 0L
        }
        if (!hasCurve()) {
            return (length / clampedSpeed()).toLong()
        }
        return maxOf(1L, (length * integralInvSpeed(0f, 1f)).toLong())
    }

    /** Source milliseconds from the clip in-point for a local output time. */
    fun sourceOffsetForOutput(localOut: Long): Int {
        val length = sourceLength()
        if (length <= 0) {
            return 0
        }
        if (!hasCurve()) {
            return (localOut * clampedSpeed()).toInt()
        }
        val total = outputLength()
        if (total <= 0) {
            return 0
        }
        val whole = integralInvSpeed(0f, 1f)
        if (whole <= 0.0) {
            return 0
        }
        val target = (localOut / total.toDouble()).coerceIn(0.0, 1.0)
        // The curve has no closed-form inverse; bisect it. 18 halvings resolve a millisecond of a
        // clip an hour long, which is finer than the source can be addressed anyway.
        var lo = 0f
        var hi = 1f
        repeat(18) {
            val mid = (lo + hi) * 0.5f
            if (integralInvSpeed(0f, mid) / whole < target) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return (((lo + hi) * 0.5f) * length).toInt()
    }

    /** Output milliseconds from the clip start for a source offset. */
    fun outputOffsetForSource(sourceOffset: Int): Long {
        val length = sourceLength()
        if (length <= 0) {
            return 0L
        }
        if (!hasCurve()) {
            return (sourceOffset / clampedSpeed()).toLong()
        }
        val u = (sourceOffset / length.toFloat()).coerceIn(0f, 1f)
        return (length * integralInvSpeed(0f, u)).toLong()
    }

    /**
     * Splits this clip's curve at source fraction [pCut]; [tail] receives the right half. A
     * uniform-speed clip has nothing to split.
     */
    fun splitCurveAt(pCut: Float, tail: EditClip) {
        val knots = curve ?: return
        if (knots.size < 2) {
            return
        }
        val cut = pCut.coerceIn(0f, 1f)
        if (cut < 0.04f || cut > 0.96f) {
            return
        }
        val sCut = speedAt(cut)
        val left = mutableListOf<SpeedPoint>()
        val right = mutableListOf<SpeedPoint>()
        val span = 1f - cut
        knots.forEach { point ->
            if (point.p <= cut + 1e-4f) {
                left.add(point.copy().also {
                    it.p = if (cut <= 1e-4f) 0f else it.p / cut
                    it.inDx /= cut
                    it.outDx /= cut
                })
            }
            if (point.p >= cut - 1e-4f) {
                right.add(point.copy().also {
                    it.p = if (span <= 1e-4f) 1f else (it.p - cut) / span
                    it.inDx /= span
                    it.outDx /= span
                })
            }
        }
        if (left.isEmpty() || left.last().p < 0.999f) {
            left.add(SpeedPoint(1f, sCut))
        }
        left.first().p = 0f
        left.last().p = 1f
        if (right.isEmpty() || right.first().p > 0.001f) {
            right.add(0, SpeedPoint(0f, sCut))
        }
        right.first().p = 0f
        right.last().p = 1f
        curve = left
        tail.curve = right
        speed = sCut
        tail.speed = sCut
    }

    /** Trapezoid of `∫ dp / speed(p)` over `[u0, u1]`. */
    private fun integralInvSpeed(u0: Float, u1: Float): Double {
        if (u1 <= u0) {
            return 0.0
        }
        val steps = 24
        var acc = 0.0
        var last = u0
        var lastInv = 1.0 / maxOf(0.05f, speedAt(u0))
        for (i in 1..steps) {
            val u = u0 + (u1 - u0) * (i / steps.toFloat())
            val inv = 1.0 / maxOf(0.05f, speedAt(u))
            acc += (u - last) * (lastInv + inv) * 0.5
            last = u
            lastInv = inv
        }
        return acc
    }

    companion object {
        const val SPEED_MIN: Float = 0.25f
        const val SPEED_MAX: Float = 8f

        fun clampSpeed(s: Float): Float = s.coerceIn(SPEED_MIN, SPEED_MAX)

        private fun bezier(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
            val inv = 1f - t
            return inv * inv * inv * a +
                3f * inv * inv * t * b +
                3f * inv * t * t * c +
                t * t * t * d
        }

        private fun bezierDerivative(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
            val inv = 1f - t
            return 3f * inv * inv * (b - a) + 6f * inv * t * (c - b) + 3f * t * t * (d - c)
        }

        /** Newton first, bisection as the fallback the handles can force by folding back on x. */
        private fun tForX(x: Float, x0: Float, cx1: Float, cx2: Float, x3: Float): Float {
            if (x <= x0) {
                return 0f
            }
            if (x >= x3) {
                return 1f
            }
            val x1 = cx1.coerceIn(x0, x3)
            val x2 = cx2.coerceIn(x0, x3)
            var s = (x - x0) / maxOf(1e-5f, x3 - x0)
            repeat(8) {
                val estimate = bezier(s, x0, x1, x2, x3) - x
                val d = bezierDerivative(s, x0, x1, x2, x3)
                if (abs(estimate) < 1e-5f) {
                    return s.coerceIn(0f, 1f)
                }
                if (abs(d) < 1e-5f) {
                    return@repeat
                }
                s -= estimate / d
            }
            var lo = 0f
            var hi = 1f
            s = s.coerceIn(0f, 1f)
            repeat(12) {
                if (bezier(s, x0, x1, x2, x3) < x) {
                    lo = s
                } else {
                    hi = s
                }
                s = (lo + hi) * 0.5f
            }
            return s
        }
    }
}
