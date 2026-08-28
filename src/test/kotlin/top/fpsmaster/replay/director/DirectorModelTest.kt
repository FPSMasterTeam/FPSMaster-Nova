package top.fpsmaster.replay.director

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditHistoryTest {

    @Test
    fun `undo reaches back forty steps and no further`() {
        val project = EditProject.create("clip", "recording", 600_000)
        val history = EditHistory()

        repeat(45) { step ->
            history.checkpoint(project)
            project.clips[0].srcIn = step * 100
        }

        assertEquals(EditHistory.LIMIT, history.depth)
        var current = project
        repeat(EditHistory.LIMIT) {
            current = assertNotNull(history.undo(current), "undo $it should still be available")
        }
        assertFalse(history.canUndo)
        assertNull(history.undo(current))
    }

    @Test
    fun `undo restores the state before the change and redo puts it back`() {
        val project = EditProject.create("clip", "recording", 60_000)
        val history = EditHistory()

        history.checkpoint(project)
        project.setSpeed(0, 4f)
        assertEquals(15_000L, project.outputDurationMillis())

        val undone = assertNotNull(history.undo(project))
        assertEquals(60_000L, undone.outputDurationMillis())

        val redone = assertNotNull(history.redo(undone))
        assertEquals(15_000L, redone.outputDurationMillis())
    }

    @Test
    fun `a snapshot is a copy, not the project itself`() {
        val project = EditProject.create("clip", "recording", 60_000)
        val history = EditHistory()

        history.checkpoint(project)
        project.clips[0].srcIn = 5_000

        val undone = assertNotNull(history.undo(project))
        assertEquals(0, undone.clips[0].srcIn)
    }
}

class ExportPlanTest {

    private val output = File("/tmp/nova-export.mp4")

    @Test
    fun `frame count rounds a partial frame period up`() {
        assertEquals(60, plan(1000L, 60).frameCount)
        assertEquals(61, plan(1001L, 60).frameCount, "the last partial frame still gets a frame")
        assertEquals(1, plan(1L, 60).frameCount)
        assertEquals(1800, plan(30_000L, 60).frameCount)
        assertEquals(720, plan(30_000L, 24).frameCount)
    }

    @Test
    fun `an empty timeline renders nothing`() {
        assertEquals(0, plan(0L, 60).frameCount)
        assertEquals(0, plan(-1L, 60).frameCount)
    }

    @Test
    fun `frames are spaced evenly across the timeline`() {
        val plan = plan(1000L, 60)
        assertEquals(0L, plan.outputMillisAt(0))
        assertEquals(500L, plan.outputMillisAt(30))
        assertTrue(plan.outputMillisAt(plan.frameCount - 1) < 1000L)
    }

    @Test
    fun `the command asks ffmpeg for h264 at the framebuffer size`() {
        val command = ExportPlan.standard(output, 1920, 1080, 5_000L).command()

        assertTrue(command.containsInOrder("-c:v", "libx264"))
        assertTrue(command.containsInOrder("-pix_fmt", "yuv420p"))
        assertTrue(command.containsInOrder("-video_size", "1920x1080"))
        assertTrue(command.containsInOrder("-pixel_format", "rgba"))
        assertFalse(command.contains("-vf"), "the source size is the output size, nothing to scale")
        assertEquals(output.absolutePath, command.last())
    }

    @Test
    fun `a framebuffer is not scaled whatever size it is`() {
        val plan = ExportPlan.standard(output, 2560, 1440, 5_000L)
        assertFalse(plan.needsScaling)
        assertFalse(plan.command().contains("-vf"))
        assertEquals(2560 * 1440 * 4, plan.bytesPerFrame)
    }

    @Test
    fun `an odd framebuffer is rounded down to what yuv420p can encode`() {
        val plan = ExportPlan.standard(output, 1367, 769, 5_000L)
        assertEquals(1366, plan.width)
        assertEquals(768, plan.height)
        assertTrue(plan.command().containsInOrder("-vf", "scale=1366:768:flags=bicubic"))
    }

    @Test
    fun `a framebuffer that has not been sized yet falls back to 720p`() {
        val plan = ExportPlan.standard(output, 0, 0, 5_000L)
        assertEquals(ExportPlan.DEFAULT_WIDTH, plan.width)
        assertEquals(ExportPlan.DEFAULT_HEIGHT, plan.height)
    }

    @Test
    fun `an explicit target size still scales`() {
        val plan = ExportPlan(output, 1920, 1080, 60, 5_000L, width = 1280, height = 720)
        assertTrue(plan.needsScaling)
        assertTrue(plan.command().containsInOrder("-vf", "scale=1280:720:flags=bicubic"))
    }

    private fun plan(duration: Long, fps: Int) = ExportPlan.standard(output, 1280, 720, duration, fps)

    private fun List<String>.containsInOrder(first: String, second: String): Boolean {
        val at = indexOf(first)
        return at >= 0 && at + 1 < size && this[at + 1] == second
    }
}

class CameraTrackTest {

    @Test
    fun `sampling on a keyframe returns exactly what was keyed`() {
        val track = CameraTrack()
        track.addPose(0, CameraPose(0.0, 64.0, 0.0, 0f, 0f, 70f))
        track.addPose(2000, CameraPose(10.0, 70.0, -4.0, 90f, -30f, 40f))

        val start = assertNotNull(track.sample(0))
        assertEquals(0.0, start.x, 1e-3)
        assertEquals(70f, start.fov, 1e-3f)

        val end = assertNotNull(track.sample(2000))
        assertEquals(10.0, end.x, 1e-3)
        assertEquals(-30f, end.pitch, 1e-3f)
        assertEquals(40f, end.fov, 1e-3f)
    }

    @Test
    fun `the camera holds still before the first key and after the last`() {
        val track = CameraTrack()
        track.addPose(1000, CameraPose(5.0, 64.0, 5.0))

        assertEquals(5.0, assertNotNull(track.sample(0)).x, 1e-3)
        assertEquals(5.0, assertNotNull(track.sample(99_000)).x, 1e-3)
    }

    @Test
    fun `an empty track leaves the live camera alone`() {
        val track = CameraTrack()
        assertTrue(track.isEmpty())
        assertNull(track.sample(500))

        val live = CameraPose(1.0, 2.0, 3.0, 45f, 10f, 80f)
        assertEquals(live, track.sample(500, live))
    }

    @Test
    fun `a channel with no keys keeps the live value while another animates`() {
        val track = CameraTrack()
        track.addValues(CameraChannel.YAW, 0, floatArrayOf(0f))
        track.addValues(CameraChannel.YAW, 1000, floatArrayOf(90f))

        val sampled = assertNotNull(track.sample(500, CameraPose(7.0, 8.0, 9.0, 0f, 15f, 65f)))
        assertEquals(7.0, sampled.x, 1e-3, "position was never keyed, so it stays where the camera is")
        assertEquals(15f, sampled.pitch, 1e-3f)
        assertTrue(sampled.yaw > 0f && sampled.yaw < 90f, "yaw was keyed, so it moves")
    }

    @Test
    fun `yaw turns the short way round`() {
        assertEquals(20f, CameraTrack.shortestArc(380f), 1e-3f)
        assertEquals(-20f, CameraTrack.shortestArc(340f), 1e-3f)
        assertEquals(-90f, CameraTrack.shortestArc(270f), 1e-3f)

        val track = CameraTrack()
        track.addValues(CameraChannel.YAW, 0, floatArrayOf(350f))
        track.addValues(CameraChannel.YAW, 1000, floatArrayOf(10f))

        val midpoint = assertNotNull(track.sample(500)).yaw
        assertTrue(
            midpoint > 340f || midpoint < 20f,
            "halfway from 350 to 10 is near 0, not near 180; was $midpoint"
        )
    }

    @Test
    fun `a cut holds its value until the next key`() {
        val track = CameraTrack()
        val first = track.addValues(CameraChannel.FOV, 0, floatArrayOf(70f))
        first.path = Transition.CUT
        track.addValues(CameraChannel.FOV, 1000, floatArrayOf(30f))

        assertEquals(70f, assertNotNull(track.sample(999)).fov, 1e-3f)
        assertEquals(30f, assertNotNull(track.sample(1000)).fov, 1e-3f)
    }

    @Test
    fun `a key dropped on top of another replaces it`() {
        val track = CameraTrack()
        track.addValues(CameraChannel.FOV, 1000, floatArrayOf(70f))
        track.addValues(CameraChannel.FOV, 1050, floatArrayOf(30f))

        assertEquals(1, track.fov.size)
        assertEquals(30f, track.fov[0].a, 1e-3f)
    }

    private fun assertEquals(expected: Double, actual: Double, tolerance: Double, message: String = "") {
        assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected, was $actual")
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float, message: String = "") {
        assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected, was $actual")
    }
}

class EditStoreTest {

    @Test
    fun `a project survives a save and load`() {
        val directory = File(System.getProperty("java.io.tmpdir"), "nova-edit-${System.nanoTime()}")
        directory.mkdirs()
        try {
            val store = EditStore(directory)
            val project = EditProject.create("shot", "recording", 60_000)
            project.splitAtOutput(20_000L)
            project.setSpeed(1, 2f)
            project.camera.addPose(1000, CameraPose(1.0, 2.0, 3.0, 45f, -10f, 60f))
            store.save(project)

            assertTrue(store.list().contains("shot"))

            val loaded = assertNotNull(store.load("shot"))
            assertEquals(2, loaded.clips.size)
            assertEquals(project.outputDurationMillis(), loaded.outputDurationMillis())
            assertEquals(1, loaded.camera.position.size)
            assertEquals(45f, loaded.camera.yaw[0].a)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a name that is not a filename is made into one`() {
        assertEquals("a_b_c", EditStore.sanitise("a/b:c"))
        assertEquals("project", EditStore.sanitise(""))
    }
}
