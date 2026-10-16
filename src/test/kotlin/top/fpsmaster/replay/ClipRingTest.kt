package top.fpsmaster.replay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipRingTest {

    private fun packet(millis: Int, size: Int = 1) = ReplayRecord.packet(millis, ByteArray(size))

    @Test
    fun `a clip needs a complete keyframe`() {
        val ring = ClipRing(1L shl 30)
        val segment = ring.beginSegment(0, listOf(byteArrayOf(1)))
        ring.add(packet(10))
        assertNull(ring.clip(), "terrain snapshot still in progress")

        ring.finishKeyframe(segment)
        val clip = assertNotNull(ring.clip())
        assertEquals(1, clip.keyframe.size)
        assertEquals(1, clip.records.size)
    }

    @Test
    fun `old segments leave the window only once the next keyframe is complete`() {
        val ring = ClipRing(1L shl 30)
        ring.finishKeyframe(ring.beginSegment(0, emptyList()))
        ring.add(packet(100))
        val second = ring.beginSegment(60_000, emptyList())
        ring.add(packet(60_100))

        ring.trim(nowMillis = 200_000, windowMillis = 60_000)
        assertEquals(2, ring.segmentCount, "second keyframe incomplete: the first is still the only start")

        ring.finishKeyframe(second)
        ring.trim(nowMillis = 200_000, windowMillis = 60_000)
        assertEquals(1, ring.segmentCount)
        val clip = assertNotNull(ring.clip())
        assertEquals(60_000, clip.baseMillis)
        assertEquals(listOf(100), clip.records.map { ClipRing.rebase(it, clip.baseMillis).millis })
    }

    @Test
    fun `segments still inside the window are kept`() {
        val ring = ClipRing(1L shl 30)
        ring.finishKeyframe(ring.beginSegment(0, emptyList()))
        ring.finishKeyframe(ring.beginSegment(60_000, emptyList()))
        ring.trim(nowMillis = 90_000, windowMillis = 120_000)
        assertEquals(2, ring.segmentCount)
        assertEquals(0, assertNotNull(ring.clip()).baseMillis, "the clip covers the whole window")
    }

    @Test
    fun `the byte budget evicts oldest segments even inside the window`() {
        val budget = 10_000L
        val ring = ClipRing(budget)
        ring.finishKeyframe(ring.beginSegment(0, emptyList()))
        repeat(10) { ring.add(packet(it, 1000)) }
        ring.finishKeyframe(ring.beginSegment(1_000, emptyList()))
        ring.add(packet(1_001, 10))

        ring.trim(nowMillis = 2_000, windowMillis = 600_000)
        assertEquals(1, ring.segmentCount)
        assertTrue(ring.totalBytes <= budget)
    }

    @Test
    fun `rebase never produces negative timestamps`() {
        assertEquals(0, ClipRing.rebase(packet(5), 10).millis)
        assertEquals(15, ClipRing.rebase(packet(25), 10).millis)
    }
}
