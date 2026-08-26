package top.fpsmaster.replay.director

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * An FFmpeg process fed raw RGBA frames on stdin.
 *
 * FFmpeg is not bundled; [isAvailable] answers whether one is on PATH so the UI can say "install
 * ffmpeg" instead of failing an hour into a render.
 *
 * Its stderr is drained on a helper thread — a full stderr pipe deadlocks the child, and the tail is
 * the only useful thing to show when an export dies.
 */
class FfmpegEncoder private constructor(
    private val process: Process,
    private val stdin: OutputStream,
    private val diagnostics: StringBuilder,
) : Closeable {

    @Volatile
    var framesWritten: Int = 0
        private set

    /** True until the pipe breaks, which is how a crashed FFmpeg shows up on this side. */
    var isAlive: Boolean = true
        private set

    fun writeFrame(frame: ByteArray, length: Int = frame.size) {
        if (!isAlive) {
            return
        }
        try {
            stdin.write(frame, 0, length)
            framesWritten++
        } catch (broken: IOException) {
            isAlive = false
            throw IOException("ffmpeg stopped reading after $framesWritten frames: ${tail()}", broken)
        }
    }

    fun writeFrame(frame: ByteBuffer) {
        val length = frame.remaining()
        val bytes = ByteArray(length)
        frame.get(bytes)
        writeFrame(bytes, length)
    }

    /** Closes stdin and waits for FFmpeg to finish writing the container. */
    fun finish(timeoutSeconds: Long = 120L): Int {
        closeStdin()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IOException("ffmpeg did not finish within ${timeoutSeconds}s: ${tail()}")
        }
        isAlive = false
        val code = process.exitValue()
        if (code != 0) {
            throw IOException("ffmpeg exited with $code: ${tail()}")
        }
        return code
    }

    /** Abandons the export. The partial file is left for the caller to delete. */
    override fun close() {
        closeStdin()
        if (process.isAlive) {
            process.destroyForcibly()
        }
        isAlive = false
    }

    fun tail(): String = synchronized(diagnostics) {
        diagnostics.toString().trim().takeLast(DIAGNOSTIC_TAIL)
    }

    private fun closeStdin() {
        try {
            stdin.flush()
            stdin.close()
        } catch (ignored: IOException) {
            // The process is already gone; waitFor/exitValue reports what happened.
        }
    }

    companion object {
        private const val DIAGNOSTIC_TAIL = 2000

        fun start(plan: ExportPlan, ffmpeg: String = ExportPlan.DEFAULT_FFMPEG): FfmpegEncoder {
            plan.output.parentFile?.mkdirs()
            val builder = ProcessBuilder(plan.command(ffmpeg))
            builder.redirectErrorStream(true)
            val process = builder.start()
            val diagnostics = StringBuilder()
            drain(process, diagnostics)
            return FfmpegEncoder(
                process,
                BufferedOutputStream(process.outputStream, 1 shl 16),
                diagnostics,
            )
        }

        fun isAvailable(ffmpeg: String = ExportPlan.DEFAULT_FFMPEG): Boolean = try {
            val probe = ProcessBuilder(ffmpeg, "-version")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(File(if (isWindows()) "NUL" else "/dev/null")))
                .start()
            probe.waitFor(5, TimeUnit.SECONDS) && probe.exitValue() == 0
        } catch (missing: Exception) {
            false
        }

        private fun isWindows(): Boolean =
            System.getProperty("os.name").orEmpty().lowercase().contains("win")

        private fun drain(process: Process, into: StringBuilder) {
            val reader = Thread({
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(into) {
                            into.append(line).append('\n')
                            if (into.length > DIAGNOSTIC_TAIL * 2) {
                                into.delete(0, into.length - DIAGNOSTIC_TAIL)
                            }
                        }
                    }
                } catch (ignored: IOException) {
                    // Process ended; nothing more to read.
                }
            }, "nova-ffmpeg-log")
            reader.isDaemon = true
            reader.start()
        }
    }
}
