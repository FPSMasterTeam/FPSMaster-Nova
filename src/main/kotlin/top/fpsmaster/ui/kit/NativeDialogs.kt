package top.fpsmaster.ui.kit

//? if >=1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import top.fpsmaster.logger
import top.fpsmaster.mc
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Links, folders and file pickers without AWT. Minecraft's Main sets `java.awt.headless=true`, so
 * `Desktop.browse` is never supported and constructing a `FileDialog` throws `HeadlessException`.
 *
 * Links and folders go through Minecraft's own launcher (`open` / `xdg-open` / `rundll32`). File
 * pickers use LWJGL's tinyfd, which every supported game version ships (lwjgl-tinyfd 3.3.1–3.4.1).
 */
object NativeDialogs {
    /** tinyfd keeps its result in static buffers, so only one dialog may be open at a time. */
    private val dialogOpen = AtomicBoolean(false)

    /** Opens [url] in the system browser. Returns false when the URL could not be handed off. */
    fun openUrl(url: String): Boolean {
        if (url.isBlank()) {
            return false
        }
        return try {
            Util.getPlatform().openUri(URI(url))
            true
        } catch (exception: Exception) {
            logger.warn("Failed to open {}", url, exception)
            false
        }
    }

    /** Opens [directory] in the system file manager. */
    fun openFolder(directory: Path): Boolean {
        return try {
            Util.getPlatform().openFile(directory.toFile())
            true
        } catch (exception: Exception) {
            logger.warn("Failed to open {}", directory, exception)
            false
        }
    }

    /**
     * Shows a native open-file dialog on a worker thread (tinyfd has no main-thread requirement on
     * any platform, and running it off the render thread keeps the game window responsive).
     * [onResult] runs on the render thread with the chosen files, or an empty list when the user
     * cancelled or the dialog could not be shown. Returns false if another dialog is already open.
     *
     * [patterns] are glob filters such as `*.png`. Titles must not contain quotes: tinyfd rejects them.
     */
    fun pickFiles(
        title: String,
        patterns: List<String>,
        description: String,
        multiple: Boolean,
        onResult: (List<File>) -> Unit
    ): Boolean {
        if (!dialogOpen.compareAndSet(false, true)) {
            return false
        }
        val worker = Thread({
            val files = try {
                parse(openDialog(title.replace("'", "").replace("\"", ""), patterns, description, multiple))
            } catch (throwable: Throwable) {
                // UnsatisfiedLinkError when the tinyfd native is missing, or a platform failure.
                logger.warn("Native file dialog failed", throwable)
                emptyList()
            } finally {
                dialogOpen.set(false)
            }
            mc.execute { onResult(files) }
        }, "FPSMaster-FileDialog")
        worker.isDaemon = true
        worker.start()
        return true
    }

    private fun openDialog(title: String, patterns: List<String>, description: String, multiple: Boolean): String? {
        MemoryStack.stackPush().use { stack ->
            val filters = if (patterns.isEmpty()) {
                null
            } else {
                stack.mallocPointer(patterns.size).also { buffer ->
                    patterns.forEach { buffer.put(stack.UTF8(it)) }
                    buffer.flip()
                }
            }
            return TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, description.ifBlank { null }, multiple)
        }
    }

    /** tinyfd joins multiple selections with '|'. */
    internal fun parse(raw: String?): List<File> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return raw.split('|').filter { it.isNotBlank() }.map(::File)
    }
}
