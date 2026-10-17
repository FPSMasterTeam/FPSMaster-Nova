package top.fpsmaster.module.impl.auxiliary

import io.github.vlouboos.standaloneevent.api.EventHandler
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.Packet
import org.lwjgl.glfw.GLFW
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.ModuleManager
import top.fpsmaster.module.value.impl.KeyValue
import top.fpsmaster.notification.NotificationManager
import top.fpsmaster.prism.overlay.NotificationCenter
import top.fpsmaster.replay.ReplayClipBuffer
import top.fpsmaster.screenCompat
import top.fpsmaster.translation.Language
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
        values.addAll(arrayOf(exportFps, followRecorder, autoRecord, clipBuffer, clipMinutes, clipKey))
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

        /** Start a full recording on joining a server, stop it on leaving. */
        val autoRecord = OptionValue("auto-record", false)

        /** Keep the last [clipMinutes] in memory so [clipKey] can save them. */
        val clipBuffer = OptionValue("clip-buffer", false)
        val clipMinutes = NumberValue("clip-minutes", 2.0, 1.0, 10.0, 1.0)
        val clipKey = KeyValue("clip-key", GLFW.GLFW_KEY_F7) { clipBuffer.getValue() }

        private var wasInWorld = false

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
        fun onClientboundPacket(packet: Packet<*>, listener: Any?) {
            if (session != null) {
                return
            }
            ReplayRecorder.onClientboundPacket(packet, (listener as? ClientPacketListener)?.registryAccess())
        }

        /** Client thread, once per tick. */
        @JvmStatic
        fun onClientTick() {
            val inWorld = session == null && mc.player != null && mc.level != null
            if (inWorld != wasInWorld) {
                wasInWorld = inWorld
                if (inWorld) onJoinWorld() else onLeaveWorld()
            }
            if (ReplayRecorder.isRecording) {
                ReplayRecorder.onClientTick()
            }
            if (inWorld && clipBuffer.getValue()) {
                ReplayClipBuffer.tick((clipMinutes.getValue() * 60_000).toInt())
            } else if (ReplayClipBuffer.isBuffering) {
                ReplayClipBuffer.stop()
            }
            session?.tick()
        }

        private fun module(): Replay? = ModuleManager.modules["replay"] as? Replay

        private fun onJoinWorld() {
            // Servers only: "join a server and it records" is the feature; singleplayer worlds are
            // left to the manual toggle.
            if (autoRecord.getValue() && !mc.isLocalServer && !ReplayRecorder.isRecording) {
                module()?.enabled = true
            }
        }

        private fun onLeaveWorld() {
            // A recording that outlives its connection would carry on into the next server's packets
            // in the same file.
            if (ReplayRecorder.isRecording) {
                module()?.enabled = false
            }
            ReplayClipBuffer.stop()
        }

        /** Saves the buffered clip; bound to [clipKey]. */
        fun saveClip() {
            if (!clipBuffer.getValue()) {
                return
            }
            val title = Language.get("replay.clip.title")
            when (ReplayClipBuffer.save(recordingsDirectory()) { file ->
                if (file != null) {
                    NotificationManager.add(title, Language.get("replay.clip.saved").format(file.name), 3f,
                        NotificationCenter.Type.SUCCESS)
                } else {
                    NotificationManager.add(title, Language.get("replay.clip.failed"), 3f, NotificationCenter.Type.ERROR)
                }
            }) {
                ReplayClipBuffer.SaveResult.STARTED ->
                    NotificationManager.add(title, Language.get("replay.clip.saving"), 2f)
                ReplayClipBuffer.SaveResult.BUSY ->
                    NotificationManager.add(title, Language.get("replay.clip.busy"), 2f, NotificationCenter.Type.WARNING)
                ReplayClipBuffer.SaveResult.NOTHING_BUFFERED ->
                    NotificationManager.add(title, Language.get("replay.clip.empty"), 2f, NotificationCenter.Type.WARNING)
            }
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

/**
 * The clip hotkey, registered on the event bus once at startup: it has to work while the Replay
 * module — whose enabled state *is* "a recording is running" — is off. Kept out of [Replay] so that
 * registering the module instance on enable does not subscribe it a second time.
 */
class ReplayHotkey {
    companion object {
        @JvmStatic
        @EventHandler
        fun onKey(event: KeyEvent) {
            val key = Replay.clipKey
            if (key.isBound() && event.key.value == key.getValue() && mc.screenCompat == null) {
                Replay.saveClip()
            }
        }
    }
}
