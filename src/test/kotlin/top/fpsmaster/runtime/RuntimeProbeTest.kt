package top.fpsmaster.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeProbeTest {

    @Test
    fun probeStaysOffWithoutItsSystemProperty() {
        assertFalse(RuntimeProbe.enabled, "the probe must never arm itself in a plain session")
    }

    @Test
    fun percentilesPickTheNearestRankAbove() {
        val samples = LongArray(100) { (it + 1).toLong() }
        assertEquals(50L, RuntimeProbe.percentile(samples, 0.50))
        assertEquals(95L, RuntimeProbe.percentile(samples, 0.95))
        assertEquals(99L, RuntimeProbe.percentile(samples, 0.99))
    }

    @Test
    fun snapshotCarriesEveryResourceFamilyTheSoakGateReads() {
        val snapshot = RuntimeProbe.snapshot("test")
        assertEquals("test", snapshot.get("reason").asString)
        listOf(
            "frames", "gcCount", "gcMillis", "heapUsedMib", "threads", "fpsmasterNonDaemonThreads",
            "texturesLive", "framebuffersLive", "browsersLive"
        ).forEach { key ->
            assertTrue(snapshot.has(key), "report is missing $key")
        }
        assertEquals(0L, snapshot.get("browsersLive").asLong, "nothing was opened in this JVM")
        assertTrue(snapshot.get("threads").asInt > 0, "the reporting thread itself must be counted")
    }

    @Test
    fun percentilesStayInBoundsOnTinyWindows() {
        val single = longArrayOf(7L)
        assertEquals(7L, RuntimeProbe.percentile(single, 0.0))
        assertEquals(7L, RuntimeProbe.percentile(single, 0.99))

        val pair = longArrayOf(3L, 9L)
        assertEquals(3L, RuntimeProbe.percentile(pair, 0.50))
        assertEquals(9L, RuntimeProbe.percentile(pair, 0.99))
    }
}
