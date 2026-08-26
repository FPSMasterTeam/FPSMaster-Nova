package top.fpsmaster.replay.director

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditProjectTest {

    private fun project(duration: Int = 60_000): EditProject =
        EditProject.create("clip", "recording", duration)

    @Test
    fun `speed stretches the output without touching the source`() {
        val project = project(60_000)
        assertEquals(60_000L, project.outputDurationMillis())

        project.setSpeed(0, 2f)
        assertEquals(30_000L, project.outputDurationMillis())
        assertEquals(60_000, project.clips[0].srcOut, "the source range is untouched by a speed change")

        project.setSpeed(0, 0.5f)
        assertEquals(120_000L, project.outputDurationMillis())
    }

    @Test
    fun `a split keeps the total output length`() {
        val project = project(60_000)
        val before = project.outputDurationMillis()

        assertTrue(project.splitAtOutput(20_000L))

        assertEquals(2, project.clips.size)
        assertEquals(before, project.outputDurationMillis())
        assertEquals(project.clips[0].srcOut, project.clips[1].srcIn, "the halves meet where the cut was")
    }

    @Test
    fun `a split at the very edge of a clip is refused`() {
        val project = project(60_000)
        assertFalse(project.splitAtOutput(0L))
        assertFalse(project.splitAtOutput(project.outputDurationMillis()))
        assertEquals(1, project.clips.size)
    }

    @Test
    fun `a trim cannot shrink a clip below the minimum source length`() {
        val project = project(60_000)
        project.trimSource(0, 10_000, 10_000)
        assertEquals(EditProject.MIN_CLIP_SOURCE, project.clips[0].sourceLength())
    }

    @Test
    fun `output time and source time are inverses of each other`() {
        val project = project(60_000)
        project.splitAtOutput(20_000L)
        project.setSpeed(1, 4f)

        for (output in longArrayOf(0L, 5_000L, 19_000L, 21_000L, 29_000L)) {
            val source = project.mapOutputToSource(output)
            val index = project.clipIndexAtOutput(output)
            val roundTrip = project.outputTimeFor(index, source)
            assertTrue(
                abs(roundTrip - output) <= 2L,
                "output $output mapped to source $source and back to $roundTrip"
            )
        }
    }

    @Test
    fun `the last clip cannot be removed`() {
        val project = project()
        assertFalse(project.removeClip(0))
        assertEquals(1, project.clips.size)
    }

    @Test
    fun `duplicating a clip repeats the same source range`() {
        val project = project(30_000)
        val at = project.duplicateClip(0)

        assertEquals(1, at)
        assertEquals(2, project.clips.size)
        assertEquals(project.clips[0].srcIn, project.clips[1].srcIn)
        assertEquals(project.clips[0].srcOut, project.clips[1].srcOut)
        assertEquals(60_000L, project.outputDurationMillis())
    }

    @Test
    fun `a speed curve lands between the rates it ramps across`() {
        val clip = EditClip(0, 10_000)
        clip.speed = 1f
        clip.enableCurve()
        clip.curve!!.first().s = 1f
        clip.curve!!.last().s = 4f

        val length = clip.outputLength()
        assertTrue(
            length in 2_500L..10_000L,
            "a ramp from 1x to 4x runs between the two constant-speed lengths, was $length"
        )

        assertEquals(1f, clip.speedAtSource(0), 0.05f)
        assertEquals(4f, clip.speedAtSource(10_000), 0.05f)
    }

    @Test
    fun `a curve survives being split in two`() {
        val project = project(20_000)
        val clip = project.clips[0]
        clip.enableCurve()
        clip.curve!!.first().s = 0.5f
        clip.curve!!.last().s = 4f
        val before = project.outputDurationMillis()

        assertTrue(project.splitAtOutput(before / 2))

        assertEquals(2, project.clips.size)
        assertTrue(project.clips[0].hasCurve())
        assertTrue(project.clips[1].hasCurve())
        val after = project.outputDurationMillis()
        assertTrue(
            abs(after - before) <= before / 20,
            "splitting a ramp keeps the movie roughly the same length: $before then $after"
        )
    }

    @Test
    fun `speed is clamped to what the timeline can address`() {
        assertEquals(EditClip.SPEED_MIN, EditClip.clampSpeed(0.01f))
        assertEquals(EditClip.SPEED_MAX, EditClip.clampSpeed(1000f))
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "expected $expected within $tolerance of $actual"
        )
    }
}
