package top.fpsmaster.replay

import org.lwjgl.glfw.GLFW
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.replay.adapter.DirectorRenderAdapter
import top.fpsmaster.screenCompat
import top.fpsmaster.setScreenCompat
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

    /** Last polled state of the roll-reset key, so holding it does not re-level every tick. */
    private var rollResetWasDown = false

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
        pollRollKeys()
        playback.tick()
        advanceOutputClock()
        applyCamera(playback.positionMillis)
    }

    /**
     * Drags the output playhead along behind the playback clock.
     *
     * Without this [outputMillis] only ever moves when the operator scrubs: the transport clock and
     * the scrubber sit still through an entire preview, and "key camera here" files its keyframe
     * wherever the playhead was last dropped rather than at what is on screen.
     */
    private fun advanceOutputClock() {
        val duration = outputDuration()
        if (duration <= 0L) {
            outputMillis = 0L
            return
        }
        var index = project.clipIndexAtOutput(outputMillis)
        var clip = project.clips.getOrNull(index) ?: return
        // Ran off the end of this clip: the edit says another one follows, so the source jumps to
        // where that clip starts instead of playing on through the material the cut removed.
        if (playback.positionMillis > clip.srcOut && index + 1 < project.clips.size) {
            index += 1
            clip = project.clips[index]
            // 只有素材真的不连续才 seek。Split 切出来的相邻两段是 tail.srcIn == clip.srcOut，
            // 播放头越过切点时已经比 srcIn 大了几毫秒——照着 srcIn 跳就是一次「往回 seek」，
            // 而往回 seek 走的是 rebuild()：拆掉世界、从 0 毫秒重放整条包流，同步跑在客户端
            // tick 上。几十分钟的录像就是几十秒的整客户端冻结，而且每越过一刀来一次，切过
            // 几刀的工程按下播放基本等于假死。位置已经落在新片段里的话什么都不用做。
            if (playback.positionMillis < clip.srcIn || playback.positionMillis > clip.srcOut) {
                playback.seekTo(clip.srcIn)
            }
        }
        outputMillis = project.outputTimeFor(index, playback.positionMillis).coerceIn(0L, duration)
    }

    /**
     * Q/E tilt the camera, R levels it, shift makes the step fine.
     *
     * Polled here rather than off a key event because these are held keys, and polled once per tick
     * rather than once per frame because the roll then moves in the same steps the rest of the pose
     * does — nudging it every frame reads as the shot shivering. Nothing runs while a screen is up,
     * so typing a filename in the replay screen does not roll the camera; that also means E opens
     * the inventory first and only tilts on the way back out, exactly as it does in Edge.
     */
    private fun pollRollKeys() {
        if (mc.screenCompat != null) {
            rollResetWasDown = false
            return
        }
        val window = windowHandle()
        val fine = isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || isDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)
        val step = if (fine) 1f else 5f
        var delta = 0f
        if (isDown(window, GLFW.GLFW_KEY_Q)) {
            delta -= step
        }
        if (isDown(window, GLFW.GLFW_KEY_E)) {
            delta += step
        }
        if (delta != 0f) {
            DirectorRenderAdapter.nudgeRoll(delta)
        }
        // Edge-triggered: holding R levels the camera once, it does not fight a nudge held with it.
        val reset = isDown(window, GLFW.GLFW_KEY_R)
        if (reset && !rollResetWasDown) {
            DirectorRenderAdapter.resetRoll()
        }
        rollResetWasDown = reset
    }

    private fun isDown(window: Long, key: Int): Boolean = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS

    private fun windowHandle(): Long {
        //? if >=1.21.11 {
        return mc.window.handle()
        //?} else {
        /*return mc.window.window*/
        //?}
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

        /** Frames the capture path could not read. Counted so a silent export cannot lie. */
        var droppedFrames: Int = 0
            private set

        var isFinished: Boolean = false
            private set

        var failure: String? = null
            private set

        val progress: Float
            get() = if (plan.frameCount <= 0) 1f else frameIndex.toFloat() / plan.frameCount

        fun begin() {
            // 导出读的是主渲染目标，也就是屏幕上有什么就烤进每一帧：导演界面自己那块面板会盖满
            // 整段视频。世界里的控制条由 ReplayOverlay 按 isExporting 自己收掉，原版 HUD 归 F1。
            mc.setScreenCompat(null)
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
            } else {
                droppedFrames++
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
                if (droppedFrames > 0) {
                    // 帧数对不上的时候，视频会比时间轴短，而且没有任何提示。
                    logger.warn("[director] $droppedFrames of ${plan.frameCount} frames could not be captured")
                }
            } catch (failed: Exception) {
                fail(failed.message ?: "ffmpeg failed")
            }
        }

        private fun fail(message: String) {
            failure = message
            isFinished = true
            encoder.close()
            // 半截的 mp4 大多打不开，留在录像目录里只会被当成一次成功的导出。
            plan.output.delete()
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
