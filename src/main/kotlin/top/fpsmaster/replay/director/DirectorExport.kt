package top.fpsmaster.replay.director

import java.io.File

/**
 * Everything about an export that can be decided before a single frame is drawn: how many frames
 * there are, where on the timeline each one sits, and the FFmpeg invocation that consumes them.
 *
 * Frames are captured at whatever size the game is running at ([sourceWidth] x [sourceHeight]) and
 * scaled to [width] x [height] by FFmpeg. Forcing the render target to 720p instead would fight the
 * client's own resize on every frame, and the scale costs nothing next to the encode.
 *
 * Kept free of Minecraft, so the arithmetic that decides how long a movie is can be tested without
 * one. Reading the pixels back is
 * [top.fpsmaster.replay.adapter.DirectorRenderAdapter]'s job.
 */
class ExportPlan(
    val output: File,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val fps: Int,
    val outputDurationMillis: Long,
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
) {
    /**
     * `ceil(outputDuration * fps / 1000)`.
     *
     * Rounding up rather than down gives the last partial frame period a frame of its own, so the
     * movie is never shorter than the timeline it came from.
     */
    val frameCount: Int = if (outputDurationMillis <= 0L || fps <= 0) {
        0
    } else {
        ((outputDurationMillis * fps + 999L) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    val bytesPerFrame: Int
        get() = sourceWidth * sourceHeight * 4

    val needsScaling: Boolean
        get() = sourceWidth != width || sourceHeight != height

    /** Output timestamp of frame [index], in milliseconds. */
    fun outputMillisAt(index: Int): Long = index.toLong() * 1000L / fps

    /**
     * Frames arrive as RGBA rows already the right way up — the capture path flips them, the same
     * way a vanilla screenshot is flipped — so there is no `vflip` here.
     */
    fun command(ffmpeg: String = DEFAULT_FFMPEG): List<String> {
        val command = mutableListOf(
            ffmpeg,
            "-y",
            "-f", "rawvideo",
            "-pixel_format", "rgba",
            "-video_size", "${sourceWidth}x$sourceHeight",
            "-framerate", fps.toString(),
            "-i", "-",
            "-an",
        )
        if (needsScaling) {
            command += listOf("-vf", "scale=$width:$height:flags=bicubic")
        }
        command += listOf(
            "-c:v", "libx264",
            "-preset", "medium",
            "-crf", "18",
            "-pix_fmt", "yuv420p",
            "-movflags", "+faststart",
            output.absolutePath,
        )
        return command
    }

    companion object {
        const val DEFAULT_WIDTH: Int = 1280
        const val DEFAULT_HEIGHT: Int = 720
        const val DEFAULT_FPS: Int = 60
        const val DEFAULT_FFMPEG: String = "ffmpeg"

        /** 720p H.264, the shape every export takes unless the caller says otherwise. */
        fun standard(
            output: File,
            sourceWidth: Int,
            sourceHeight: Int,
            outputDurationMillis: Long,
            fps: Int = DEFAULT_FPS,
        ): ExportPlan = ExportPlan(output, sourceWidth, sourceHeight, fps, outputDurationMillis)
    }
}
