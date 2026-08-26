package top.fpsmaster.replay

import top.fpsmaster.logger
import top.fpsmaster.replay.adapter.DirectorRenderAdapter
import top.fpsmaster.replay.director.CameraChannel
import top.fpsmaster.replay.director.EditHistory
import top.fpsmaster.replay.director.EditProject
import top.fpsmaster.replay.director.EditStore
import top.fpsmaster.replay.director.ExportPlan
import top.fpsmaster.replay.director.FfmpegEncoder
import java.io.File

/**
 * An open recording and the edit being made of it.
 *
 * The edit model itself knows nothing about Minecraft — it maps output time to source time and
 * nothing more. This is where that mapping is spent: seeking the playback to the source moment the
 * timeline points at, and placing the camera where the keyframes say it should be.
 */
class DirectorSession(
    val playback: ReplayPlayback,
    val store: EditStore,
    var project: EditProject,
) {
    val history = EditHistory()

    /** Position on the *output* timeline, which is what the operator scrubs. */
    var outputMillis: Long = 0L
        private set

    var export: ExportJob? = null
        private set

    val isExporting: Boolean
        get() = export != null

    fun sourceDuration(): Int = playback.durationMillis

    fun outputDuration(): Long = project.outputDurationMillis()

    /** Records the project so the next mutation can be undone. */
    fun checkpoint() = history.checkpoint(project)

    fun undo() {
        history.undo(project)?.let {
            project = it
            scrubTo(outputMillis)
        }
    }

    fun redo() {
        history.redo(project)?.let {
            project = it
            scrubTo(outputMillis)
        }
    }

    fun save() {
        project.ensureDuration(sourceDuration())
        store.save(project)
    }

    /** Moves the preview to a point on the output timeline. */
    fun scrubTo(millis: Long) {
        project.ensureDuration(sourceDuration())
        outputMillis = millis.coerceIn(0L, outputDuration())
        val source = project.mapOutputToSource(outputMillis)
        playback.seekTo(source)
        applyCamera(source)
    }

    /** Client thread, once per frame. */
    fun tick() {
        val running = export
        if (running != null) {
            running.step()
            if (running.isFinished) {
                export = null
            }
            return
        }
        playback.tick()
        applyCamera(playback.positionMillis)
    }

    /** Keys every camera channel at the source moment currently on screen. */
    fun keyCameraHere() {
        val pose = DirectorRenderAdapter.currentPose() ?: return
        checkpoint()
        project.camera.addPose(project.mapOutputToSource(outputMillis), pose)
    }

    fun clearCameraHere(window: Int = 200) {
        val source = project.mapOutputToSource(outputMillis)
        checkpoint()
        CameraChannel.entries.forEach { channel ->
            project.camera.nearest(channel, source, window)?.let { project.camera.remove(channel, it) }
        }
    }

    private fun applyCamera(sourceMillis: Int) {
        if (project.camera.isEmpty()) {
            DirectorRenderAdapter.clear()
            return
        }
        val pose = project.camera.sample(sourceMillis, DirectorRenderAdapter.currentPose()) ?: return
        DirectorRenderAdapter.apply(pose)
    }

    /**
     * Starts a render. Returns null with the reason logged when there is nothing to render or no
     * FFmpeg to render it with.
     */
    fun startExport(output: File, fps: Int = ExportPlan.DEFAULT_FPS): ExportJob? {
        if (isExporting) {
            return export
        }
        val duration = outputDuration()
        if (duration <= 0L) {
            logger.warn("[director] the timeline is empty")
            return null
        }
        if (!FfmpegEncoder.isAvailable()) {
            logger.warn("[director] ffmpeg is not on PATH")
            return null
        }
        val plan = ExportPlan.standard(
            output,
            DirectorRenderAdapter.framebufferWidth(),
            DirectorRenderAdapter.framebufferHeight(),
            duration,
            fps,
        )
        val encoder = try {
            FfmpegEncoder.start(plan)
        } catch (failure: Exception) {
            logger.warn("[director] ffmpeg would not start: ${failure.message}")
            return null
        }
        playback.pause()
        val job = ExportJob(plan, encoder)
        export = job
        job.begin()
        return job
    }

    fun cancelExport() {
        export?.cancel()
        export = null
    }

    /**
     * One frame per rendered frame.
     *
     * The timeline is moved to a frame's position and *then* the frame is captured on the next pass,
     * because what [DirectorRenderAdapter.captureFrame] reads is the frame the client has already
     * drawn — the one for the position set last time round.
     */
    inner class ExportJob(val plan: ExportPlan, private val encoder: FfmpegEncoder) {
        var frameIndex: Int = 0
            private set

        var isFinished: Boolean = false
            private set

        var failure: String? = null
            private set

        val progress: Float
            get() = if (plan.frameCount <= 0) 1f else frameIndex.toFloat() / plan.frameCount

        fun begin() {
            scrubTo(0L)
        }

        fun step() {
            if (isFinished) {
                return
            }
            if (frameIndex >= plan.frameCount) {
                finish()
                return
            }
            val frame = DirectorRenderAdapter.captureFrame()
            if (frame != null) {
                try {
                    encoder.writeFrame(frame)
                } catch (broken: Exception) {
                    fail(broken.message ?: "ffmpeg stopped")
                    return
                }
            }
            frameIndex++
            if (frameIndex >= plan.frameCount) {
                finish()
                return
            }
            scrubTo(plan.outputMillisAt(frameIndex))
        }

        private fun finish() {
            isFinished = true
            try {
                encoder.finish()
                logger.info("[director] wrote ${plan.output.name} (${encoder.framesWritten} frames)")
            } catch (failed: Exception) {
                fail(failed.message ?: "ffmpeg failed")
            }
        }

        private fun fail(message: String) {
            failure = message
            isFinished = true
            encoder.close()
            logger.warn("[director] export failed: $message")
        }

        fun cancel() {
            if (isFinished) {
                return
            }
            isFinished = true
            failure = "cancelled"
            encoder.close()
            plan.output.delete()
        }
    }
}
