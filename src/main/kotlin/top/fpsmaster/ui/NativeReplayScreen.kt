package top.fpsmaster.ui

import net.minecraft.network.chat.Component
import top.fpsmaster.mc
import top.fpsmaster.module.impl.auxiliary.Replay
import top.fpsmaster.prism.theme.Metrics
import top.fpsmaster.prism.widget.Chrome
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.replay.NovaReplayFile
import top.fpsmaster.replay.ReplayRecorder
import top.fpsmaster.replay.director.EditClip
import top.fpsmaster.setScreenCompat
import top.fpsmaster.ui.kit.ToolkitScreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives the replay and director model from the existing toolkit chrome.
 *
 * Deliberately plain: a list of recordings on the left, a transport and the clip under the playhead
 * on the right. Everything it can do is a call into [top.fpsmaster.replay.DirectorSession] — the
 * screen holds no edit state of its own, so what it shows is what would be exported.
 */
class NativeReplayScreen(
    private val parent: net.minecraft.client.gui.screens.Screen?,
) : ToolkitScreen(Component.literal("Replay")) {

    private var status: String = ""
    private var selected: File? = null

    override fun renderUi(ui: UiFrame) {
        Chrome.veil(ui, 1f)
        val w = 460f
        val h = 260f
        val x = (width - w) / 2f
        val y = (height - h) / 2f
        Chrome.panel(ui, x, y, w, h)

        ui.canvas().drawString(ui.font(16), "Replay", x + 16f, y + 14f, ui.theme().textPrimary())
        ui.canvas().drawString(ui.font(10), status, x + 16f, y + h - 18f, ui.theme().textSecondary())

        drawRecordingControls(ui, x + 16f, y + 36f)
        Chrome.hairlineV(ui, x + LIST_WIDTH + 24f, y + 32f, h - 56f)
        drawRecordings(ui, x + 16f, y + 60f, h - 100f)

        val session = Replay.session
        if (session == null) {
            drawIdlePane(ui, x + LIST_WIDTH + 36f, y + 60f)
        } else {
            drawDirector(ui, session, x + LIST_WIDTH + 36f, y + 60f, w - LIST_WIDTH - 52f)
        }

        if (Chrome.button(ui, x + w - 76f, y + h - 30f, 60f, Metrics.BTN_H, "Close", Chrome.ButtonStyle.DEFAULT)) {
            close()
        }
    }

    private fun drawRecordingControls(ui: UiFrame, x: Float, y: Float) {
        val recording = ReplayRecorder.isRecording
        val label = if (recording) "Stop (${ReplayRecorder.recordsWritten})" else "Record"
        val style = if (recording) Chrome.ButtonStyle.DANGER else Chrome.ButtonStyle.PRIMARY
        if (Chrome.button(ui, x, y, LIST_WIDTH, Metrics.BTN_H, label, style)) {
            val module = top.fpsmaster.module.ModuleManager.modules["replay"]
            if (module != null) {
                module.enabled = !recording
                status = if (module.enabled) "recording" else "stopped"
            } else {
                status = "the replay module is not registered"
            }
        }
    }

    private fun drawRecordings(ui: UiFrame, x: Float, y: Float, height: Float) {
        val files = Replay.recordings()
        if (files.isEmpty()) {
            ui.canvas().drawString(ui.font(10), "no recordings yet", x, y + 6f, ui.theme().textDisabled())
            return
        }
        var rowY = y
        for (file in files) {
            if (rowY + ROW_HEIGHT > y + height) {
                break
            }
            val isSelected = file == selected
            if (isSelected) {
                Chrome.selectedCard(ui, x, rowY, LIST_WIDTH, ROW_HEIGHT)
            } else {
                Chrome.card(ui, x, rowY, LIST_WIDTH, ROW_HEIGHT, ui.hovered(x, rowY, LIST_WIDTH, ROW_HEIGHT), false)
            }
            ui.canvas().drawString(
                ui.font(10),
                file.name.removeSuffix(ReplayRecorder.EXTENSION),
                x + 8f,
                rowY + 5f,
                ui.theme().textPrimary(),
            )
            ui.canvas().drawString(
                ui.font(9),
                "${DATE.format(Date(file.lastModified()))}  ${file.length() shr 10}KB",
                x + 8f,
                rowY + 16f,
                ui.theme().textSecondary(),
            )
            if (ui.clicked(x, rowY, LIST_WIDTH, ROW_HEIGHT)) {
                selected = file
                status = describe(file)
            }
            rowY += ROW_HEIGHT + 4f
        }
    }

    private fun drawIdlePane(ui: UiFrame, x: Float, y: Float) {
        val file = selected
        if (file == null) {
            ui.canvas().drawString(ui.font(10), "select a recording", x, y + 6f, ui.theme().textDisabled())
            return
        }
        if (Chrome.button(ui, x, y, 90f, Metrics.BTN_H, "Play", Chrome.ButtonStyle.PRIMARY)) {
            status = Replay.open(file) ?: "playing ${file.name}"
            if (Replay.session != null) {
                mc.setScreenCompat(null)
            }
        }
    }

    private fun drawDirector(
        ui: UiFrame,
        session: top.fpsmaster.replay.DirectorSession,
        x: Float,
        y: Float,
        width: Float,
    ) {
        val duration = session.outputDuration()
        val playback = session.playback

        ui.canvas().drawString(
            ui.font(10),
            "${time(session.outputMillis)} / ${time(duration)}   x${"%.2f".format(playback.speed)}",
            x,
            y,
            ui.theme().textPrimary(),
        )

        val scrubY = y + 16f
        val fraction = if (duration <= 0L) 0f else session.outputMillis.toFloat() / duration
        val moved = Chrome.slider(ui, SCRUB_KEY, x, scrubY, width, fraction)
        if (moved != fraction) {
            session.scrubTo((moved.coerceIn(0f, 1f) * duration).toLong())
        }

        var row = scrubY + 22f
        val third = (width - 16f) / 3f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, if (playback.isPaused) "Play" else "Pause", Chrome.ButtonStyle.PRIMARY)) {
            if (playback.isPaused) playback.play() else playback.pause()
        }
        if (Chrome.button(ui, x + third + 8f, row, third, Metrics.BTN_H, "Slower", Chrome.ButtonStyle.DEFAULT)) {
            playback.speed = playback.speed / 2f
        }
        if (Chrome.button(ui, x + (third + 8f) * 2f, row, third, Metrics.BTN_H, "Faster", Chrome.ButtonStyle.DEFAULT)) {
            playback.speed = playback.speed * 2f
        }

        row += Metrics.BTN_H + 6f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, "Split", Chrome.ButtonStyle.DEFAULT)) {
            session.checkpoint()
            status = if (session.project.splitAtOutput(session.outputMillis)) {
                "split into ${session.project.clips.size} clips"
            } else {
                "too close to a clip edge to split"
            }
        }
        if (Chrome.button(ui, x + third + 8f, row, third, Metrics.BTN_H, "Key camera", Chrome.ButtonStyle.DEFAULT)) {
            session.keyCameraHere()
            status = "camera keyed"
        }
        if (Chrome.button(ui, x + (third + 8f) * 2f, row, third, Metrics.BTN_H, "Clear key", Chrome.ButtonStyle.DEFAULT)) {
            session.clearCameraHere()
            status = "camera key cleared"
        }

        row += Metrics.BTN_H + 6f
        drawClip(ui, session, x, row, width, third)

        row += Metrics.BTN_H + 24f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, "Undo", Chrome.ButtonStyle.DEFAULT)) {
            session.undo()
            status = "undo (${session.history.depth} left)"
        }
        if (Chrome.button(ui, x + third + 8f, row, third, Metrics.BTN_H, "Redo", Chrome.ButtonStyle.DEFAULT)) {
            session.redo()
        }
        if (Chrome.button(ui, x + (third + 8f) * 2f, row, third, Metrics.BTN_H, exportLabel(session), Chrome.ButtonStyle.PRIMARY)) {
            toggleExport(session)
        }

        row += Metrics.BTN_H + 6f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, "Stop replay", Chrome.ButtonStyle.DANGER)) {
            Replay.close()
            status = "closed"
        }
    }

    private fun drawClip(
        ui: UiFrame,
        session: top.fpsmaster.replay.DirectorSession,
        x: Float,
        y: Float,
        width: Float,
        third: Float,
    ) {
        val index = session.project.clipIndexAtOutput(session.outputMillis)
        val clip = session.project.clips.getOrNull(index) ?: return
        ui.canvas().drawString(
            ui.font(9),
            "clip ${index + 1}/${session.project.clips.size}  ${time(clip.srcIn.toLong())}-${time(clip.srcOut.toLong())}" +
                "  speed ${"%.2f".format(clip.clampedSpeed())}${if (clip.hasCurve()) " curve" else ""}",
            x,
            y,
            ui.theme().textSecondary(),
        )
        val row = y + 12f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, "Trim in", Chrome.ButtonStyle.DEFAULT)) {
            session.checkpoint()
            val source = session.project.mapOutputToSource(session.outputMillis)
            session.project.trimSource(index, source, clip.srcOut)
            session.scrubTo(session.outputMillis)
        }
        if (Chrome.button(ui, x + third + 8f, row, third, Metrics.BTN_H, "Trim out", Chrome.ButtonStyle.DEFAULT)) {
            session.checkpoint()
            val source = session.project.mapOutputToSource(session.outputMillis)
            session.project.trimSource(index, clip.srcIn, source)
            session.scrubTo(session.outputMillis)
        }
        if (Chrome.button(ui, x + (third + 8f) * 2f, row, third, Metrics.BTN_H, "Speed x2", Chrome.ButtonStyle.DEFAULT)) {
            session.checkpoint()
            val next = if (clip.clampedSpeed() >= EditClip.SPEED_MAX) EditClip.SPEED_MIN else clip.clampedSpeed() * 2f
            session.project.setSpeed(index, next)
            session.scrubTo(session.outputMillis)
        }
    }

    private fun exportLabel(session: top.fpsmaster.replay.DirectorSession): String {
        val job = session.export ?: return "Export"
        return "Cancel ${(job.progress * 100f).toInt()}%"
    }

    private fun toggleExport(session: top.fpsmaster.replay.DirectorSession) {
        if (session.isExporting) {
            session.cancelExport()
            status = "export cancelled"
            return
        }
        val output = File(Replay.recordingsDirectory(), "${session.project.name}.mp4")
        val job = session.startExport(output, Replay.exportFps.getValue().toInt())
        status = if (job == null) {
            "export could not start — is ffmpeg on PATH?"
        } else {
            "rendering ${job.plan.frameCount} frames to ${output.name}"
        }
    }

    private fun describe(file: File): String {
        val header = try {
            NovaReplayFile.readHeader(file)
        } catch (unreadable: Exception) {
            return "unreadable: ${unreadable.message}"
        }
        return "${header.profile.recorderName} on ${header.profile.serverAddress}" +
            " (${header.minecraftVersion}, ${header.profile.dimension})"
    }

    private fun close() {
        mc.setScreenCompat(parent)
    }

    override fun handleEscape(): Boolean {
        close()
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    private fun time(millis: Long): String {
        val total = millis / 1000L
        return "%d:%02d".format(total / 60L, total % 60L)
    }

    private companion object {
        const val LIST_WIDTH = 150f
        const val ROW_HEIGHT = 28f
        val SCRUB_KEY = Any()
        val DATE = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)
    }
}
