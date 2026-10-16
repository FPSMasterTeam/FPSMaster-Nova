package top.fpsmaster.config

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.deleteIfExists

/**
 * Whole-file replacement that never leaves a truncated file behind: write a sibling temp file,
 * fsync it, then rename it over the target. A crash or power cut mid-write leaves either the old
 * file or the new one.
 */
object AtomicFiles {
    /**
     * [ownerOnly]: on POSIX file systems the file is created rw------- (credentials). Windows keeps
     * the per-user ACL inherited from the profile directory.
     */
    fun write(path: Path, content: String, ownerOnly: Boolean = false) {
        Files.createDirectories(path.parent)
        // pid + nanoTime: two clients (or two threads) writing the same target never share a temp.
        val tempPath = path.resolveSibling(
            "${path.fileName}.${ProcessHandle.current().pid()}.${System.nanoTime()}.tmp"
        )
        try {
            FileChannel.open(tempPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                if (ownerOnly && supportsPosix(path)) {
                    Files.setPosixFilePermissions(tempPath, PosixFilePermissions.fromString("rw-------"))
                }
                val buffer = ByteBuffer.wrap(content.toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }
            try {
                Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (unsupported: AtomicMoveNotSupportedException) {
                // Some network drives cannot rename atomically; a plain replace is the best left.
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            tempPath.deleteIfExists()
        }
    }

    private fun supportsPosix(path: Path): Boolean =
        runCatching { path.fileSystem.supportedFileAttributeViews().contains("posix") }.getOrDefault(false)
}
