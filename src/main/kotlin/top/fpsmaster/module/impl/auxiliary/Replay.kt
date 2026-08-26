package top.fpsmaster.module.impl.auxiliary

import net.minecraft.network.protocol.Packet
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue
import top.fpsmaster.replay.DirectorSession
import top.fpsmaster.replay.ReplayFormatException
import top.fpsmaster.replay.ReplayPlayback
import top.fpsmaster.replay.ReplayRecorder
import top.fpsmaster.replay.ReplayVersionMismatchException
import top.fpsmaster.replay.adapter.ReplayPlaybackUnavailableException
import top.fpsmaster.replay.director.EditProject
import top.fpsmaster.replay.director.EditStore
import java.io.File

/**
 * Records what the server sends and plays it back.
 *
 * Enabling the module starts a recording; disabling it stops one. Playback and the director are
 * driven from the screen rather than the toggle, because a recording being watched is not a
 * recording being made and the two never overlap.
 */
class Replay : Module("replay", Category.AUXILIARY) {

    override val persistEnabled: Boolean = false

    init {
        values.addAll(arrayOf(exportFps, followRecorder))
    }

    override fun onEnable() {
        if (ReplayRecorder.start(recordingsDirectory()) == null) {
            enabled = false
        }
    }

    override fun onDisable() {
        ReplayRecorder.stop()
    }

    companion object {
        val exportFps = NumberValue("export-fps", 60.0, 24.0, 120.0, 1.0)
        val followRecorder = OptionValue("follow-recorder", true)

        /** The open replay, or null when nothing is being watched. */
        @JvmStatic
        var session: DirectorSession? = null
            private set

        val isPlaying: Boolean
            get() = session != null

        fun recordingsDirectory(): File =
            File(mc.gameDirectory, "fpsmaster/replays").also { it.mkdirs() }

        fun projectsDirectory(): File =
            File(recordingsDirectory(), "projects").also { it.mkdirs() }

        fun recordings(): List<File> =
            (recordingsDirectory().listFiles { file -> file.name.endsWith(ReplayRecorder.EXTENSION) } ?: emptyArray())
                .sortedByDescending { it.lastModified() }

        /**
         * Network thread, once per decoded clientbound packet and before it is handled.
         *
         * Ignored while a replay is open: the packets going past then are the ones this very module
         * is feeding into the isolated listener, and recording them would record a recording.
         */
        @JvmStatic
        fun onClientboundPacket(packet: Packet<*>) {
            if (session != null || !ReplayRecorder.isRecording) {
                return
            }
            ReplayRecorder.onClientboundPacket(packet)
        }

        /** Client thread, once per tick. */
        @JvmStatic
        fun onClientTick() {
            if (ReplayRecorder.isRecording) {
                ReplayRecorder.onClientTick()
            }
            session?.tick()
        }

        /** Opens a recording for watching and editing. Returns the failure to show, or null. */
        fun open(file: File): String? {
            close()
            val playback = try {
                ReplayPlayback.load(file)
            } catch (mismatch: ReplayVersionMismatchException) {
                return mismatch.message
            } catch (malformed: ReplayFormatException) {
                return malformed.message
            } catch (unreadable: Exception) {
                return "could not read ${file.name}: ${unreadable.message}"
            }
            playback.followRecorder = followRecorder.getValue()
            try {
                playback.open()
            } catch (unavailable: ReplayPlaybackUnavailableException) {
                return unavailable.message
            } catch (failure: Exception) {
                return "playback could not start: ${failure.message}"
            }
            val store = EditStore(projectsDirectory())
            val name = file.name.removeSuffix(ReplayRecorder.EXTENSION)
            val project = store.load(name)
                ?: EditProject.create(name, name, playback.durationMillis)
            project.ensureDuration(playback.durationMillis)
            session = DirectorSession(playback, store, project)
            logger.info("[replay] playing ${file.name}")
            return null
        }

        fun close() {
            session?.let { open ->
                open.cancelExport()
                runCatching { open.save() }
                open.playback.close()
            }
            session = null
        }
    }
}
