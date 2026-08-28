package top.fpsmaster.ui

import net.minecraft.network.chat.Component
import top.fpsmaster.mc
import top.fpsmaster.module.impl.auxiliary.Replay
import top.fpsmaster.prism.theme.Metrics
import top.fpsmaster.prism.widget.Chrome
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.replay.NovaReplayFile
import top.fpsmaster.replay.adapter.DirectorRenderAdapter
import top.fpsmaster.replay.ReplayRecorder
import top.fpsmaster.replay.director.EditClip
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
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
 *
 * [parent] is where Close goes. From the main menu that is the menu; from the world (Escape during
 * playback, see `MixinReplayPauseMenu`) it is null, which puts the player back in the world with
 * [ReplayOverlay] still showing the transport. Both are the same screen on purpose — a viewer who
 * pauses mid-flight wants the same buttons the editor has, not a reduced set.
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

        ui.canvas().drawString(ui.font(16), tr("replay.title"), x + 16f, y + 14f, ui.theme().textPrimary())
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

        // 从世界里按 ESC 进来的时候 parent 是 null，关掉就回到世界，按钮得这么写才不骗人。
        val closeLabel = if (parent == null) tr("replay.back") else tr("replay.close")
        if (Chrome.button(ui, x + w - 76f, y + h - 30f, 60f, Metrics.BTN_H, closeLabel, Chrome.ButtonStyle.DEFAULT)) {
            close()
        }
    }

    private fun drawRecordingControls(ui: UiFrame, x: Float, y: Float) {
        val recording = ReplayRecorder.isRecording
        val label =
            if (recording) tr("replay.record.stop").format(ReplayRecorder.recordsWritten)
            else tr("replay.record")
        val style = if (recording) Chrome.ButtonStyle.DANGER else Chrome.ButtonStyle.PRIMARY
        if (Chrome.button(ui, x, y, LIST_WIDTH, Metrics.BTN_H, label, style)) {
            val module = top.fpsmaster.module.ModuleManager.modules["replay"]
            if (module != null) {
                module.enabled = !recording
                status = if (module.enabled) tr("replay.status.recording") else tr("replay.status.stopped")
            } else {
                status = tr("replay.status.module.missing")
            }
        }
    }

    private fun drawRecordings(ui: UiFrame, x: Float, y: Float, height: Float) {
        val files = Replay.recordings()
        if (files.isEmpty()) {
            ui.canvas().drawString(ui.font(10), tr("replay.empty"), x, y + 6f, ui.theme().textDisabled())
            ui.canvas().drawString(ui.font(9), tr("replay.empty.tip"), x, y + 20f, ui.theme().textDisabled())
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
            ui.canvas().drawString(ui.font(10), tr("replay.select"), x, y + 6f, ui.theme().textDisabled())
            return
        }
        if (Chrome.button(ui, x, y, 90f, Metrics.BTN_H, tr("replay.play"), Chrome.ButtonStyle.PRIMARY)) {
            val failure = Replay.open(file)
            status = failure ?: tr("replay.status.playing").format(file.name)
            // 只有真的进了世界才关界面：控制条挂在 ReplayOverlay 上，而它在 mc.player 为空时
            // 直接返回不画。从主菜单打开一个回放、世界还没起来就把界面关掉的话，玩家落到一张
            // 什么都没有的标题画面上——没有控制条，也没有刚才那个回放列表，只能以为点崩了。
            if (Replay.session != null && mc.level != null) {
                // 直接掉进世界里看回放，控制条交给 ReplayOverlay，按 ESC 能把这个界面叫回来。
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

        // 「关键帧相机」记的是这一刻实际在渲的倾斜和视角，不是玩家滑条上的值，所以得让操作者
        // 先看见它们再决定按不按。倾斜只能在世界里用 Q/E 调（这个界面开着的时候按键不轮询），
        // 回来看到的就是待记的数。
        val poseFont = ui.font(9)
        val pose = tr("replay.pose").format(
            "%.0f".format(DirectorRenderAdapter.rollDegrees),
            "%.0f".format(DirectorRenderAdapter.activeFov()),
        )
        ui.canvas().drawString(
            poseFont,
            pose,
            x + width - poseFont.measure(pose),
            y + 1f,
            ui.theme().textSecondary(),
        )

        val scrubY = y + 16f
        val fraction = if (duration <= 0L) 0f else session.outputMillis.toFloat() / duration
        val moved = Chrome.slider(ui, SCRUB_KEY, x, scrubY, width, fraction)
        if (moved != fraction) {
            session.scrubTo((moved.coerceIn(0f, 1f) * duration).toLong())
        }

        var row = scrubY + 22f
        val third = (width - 16f) / 3f
        val playLabel = if (playback.isPaused) tr("replay.transport.play") else tr("replay.transport.pause")
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, playLabel, Chrome.ButtonStyle.PRIMARY)) {
            if (playback.isPaused) playback.play() else playback.pause()
        }
        if (Chrome.button(ui, x + third + 8f, row, third, Metrics.BTN_H, tr("replay.slower"), Chrome.ButtonStyle.DEFAULT)) {
            playback.speed = playback.speed / 2f
        }
        if (Chrome.button(ui, x + (third + 8f) * 2f, row, third, Metrics.BTN_H, tr("replay.faster"), Chrome.ButtonStyle.DEFAULT)) {
            playback.speed = playback.speed * 2f
        }

        row += Metrics.BTN_H + 6f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, tr("replay.split"), Chrome.ButtonStyle.DEFAULT)) {
            session.checkpoint()
            status = if (session.project.splitAtOutput(session.outputMillis)) {
                tr("replay.status.split").format(session.project.clips.size)
            } else {
                tr("replay.status.split.tooclose")
            }
        }
        if (Chrome.button(ui, x + third + 8f, row, third, Metrics.BTN_H, tr("replay.key.camera"), Chrome.ButtonStyle.DEFAULT)) {
            session.keyCameraHere()
            status = tr("replay.status.keyed")
        }
        if (Chrome.button(ui, x + (third + 8f) * 2f, row, third, Metrics.BTN_H, tr("replay.key.clear"), Chrome.ButtonStyle.DEFAULT)) {
            session.clearCameraHere()
            status = tr("replay.status.key.cleared")
        }

        row += Metrics.BTN_H + 6f
        drawClip(ui, session, x, row, width, third)

        row += Metrics.BTN_H + 24f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, tr("replay.undo"), Chrome.ButtonStyle.DEFAULT)) {
            session.undo()
            status = tr("replay.status.undo").format(session.history.depth)
        }
        if (Chrome.button(ui, x + third + 8f, row, third, Metrics.BTN_H, tr("replay.redo"), Chrome.ButtonStyle.DEFAULT)) {
            session.redo()
        }
        if (Chrome.button(ui, x + (third + 8f) * 2f, row, third, Metrics.BTN_H, exportLabel(session), Chrome.ButtonStyle.PRIMARY)) {
            toggleExport(session)
        }

        row += Metrics.BTN_H + 6f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, tr("replay.stop"), Chrome.ButtonStyle.DANGER)) {
            Replay.close()
            status = tr("replay.status.closed")
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
        val summary = tr("replay.clip").format(
            index + 1,
            session.project.clips.size,
            time(clip.srcIn.toLong()),
            time(clip.srcOut.toLong()),
            "%.2f".format(clip.clampedSpeed()),
        )
        ui.canvas().drawString(
            ui.font(9),
            summary + if (clip.hasCurve()) tr("replay.clip.curve") else "",
            x,
            y,
            ui.theme().textSecondary(),
        )
        val row = y + 12f
        if (Chrome.button(ui, x, row, third, Metrics.BTN_H, tr("replay.trim.in"), Chrome.ButtonStyle.DEFAULT)) {
            session.checkpoint()
            val source = session.project.mapOutputToSource(session.outputMillis)
            session.project.trimSource(index, source, clip.srcOut)
            session.scrubTo(session.outputMillis)
        }
        if (Chrome.button(ui, x + third + 8f, row, third, Metrics.BTN_H, tr("replay.trim.out"), Chrome.ButtonStyle.DEFAULT)) {
            session.checkpoint()
            val source = session.project.mapOutputToSource(session.outputMillis)
            session.project.trimSource(index, clip.srcIn, source)
            session.scrubTo(session.outputMillis)
        }
        if (Chrome.button(ui, x + (third + 8f) * 2f, row, third, Metrics.BTN_H, tr("replay.speed.double"), Chrome.ButtonStyle.DEFAULT)) {
            session.checkpoint()
            val next = if (clip.clampedSpeed() >= EditClip.SPEED_MAX) EditClip.SPEED_MIN else clip.clampedSpeed() * 2f
            session.project.setSpeed(index, next)
            session.scrubTo(session.outputMillis)
        }
    }

    private fun exportLabel(session: top.fpsmaster.replay.DirectorSession): String {
        val job = session.export ?: return tr("replay.export")
        return tr("replay.export.cancel").format((job.progress * 100f).toInt())
    }

    private fun toggleExport(session: top.fpsmaster.replay.DirectorSession) {
        if (session.isExporting) {
            session.cancelExport()
            status = tr("replay.status.export.cancelled")
            return
        }
        val output = File(Replay.recordingsDirectory(), "${session.project.name}.mp4")
        val job = session.startExport(output, Replay.exportFps.getValue().toInt())
        status = if (job == null) {
            tr("replay.status.export.failed")
        } else {
            tr("replay.status.export.started").format(job.plan.frameCount, output.name)
        }
    }

    private fun describe(file: File): String {
        val header = try {
            NovaReplayFile.readHeader(file)
        } catch (unreadable: Exception) {
            return tr("replay.status.unreadable").format(unreadable.message ?: "")
        }
        return "${header.profile.recorderName} on ${header.profile.serverAddress}" +
            " (${header.minecraftVersion}, ${header.profile.dimension})"
    }

    private fun close() {
        // Stop 会把回放的假连接断掉，而断连顺带清掉了世界。从世界里按 ESC 进来的时候 parent 是
        // null，「关掉就回到世界」这时候已经不成立了：世界没了，屏幕设成 null 只剩一片什么都
        // 没有、也点不动的灰。没有世界可回就回标题画面。
        if (parent == null && mc.level == null) {
            mc.setScreenCompat(net.minecraft.client.gui.screens.TitleScreen())
            return
        }
        mc.setScreenCompat(parent)
    }

    override fun handleEscape(): Boolean {
        close()
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = true

    private companion object {
        const val LIST_WIDTH = 150f
        const val ROW_HEIGHT = 28f
        val SCRUB_KEY = Any()
        val DATE = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)

        fun tr(key: String): String = Language.get(key)

        /** `0:07` / `12:03`。回放最长也就几十分钟，不做小时位。 */
        fun time(millis: Long): String {
            val total = millis / 1000L
            return "%d:%02d".format(total / 60L, total % 60L)
        }
    }
}
