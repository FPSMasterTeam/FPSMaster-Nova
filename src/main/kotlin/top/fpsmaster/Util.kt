package top.fpsmaster

import com.mojang.blaze3d.systems.RenderSystem
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11

val logger: Logger = LogManager.getLogger("FPSMaster")

fun checkAccelerationSupport(): Boolean {
    return try {
        RenderSystem.assertOnRenderThread()

        val capabilities = GL.getCapabilities()
        val vendor = GL11.glGetString(GL11.GL_VENDOR) ?: ""
        val renderer = GL11.glGetString(GL11.GL_RENDERER) ?: ""

        logger.info("GPU Vendor: $vendor")
        logger.info("GPU Renderer: $renderer")

        // Check if the GPU is NVIDIA or AMD as
        // we could not get this feature to work reliably on Intel GPUs.
        // On Intel GPU (Intel ARC), it does not work as well and is reported:
        // https://github.com/IGCIT/Intel-GPU-Community-Issue-Tracker-IGCIT/issues/1143

        val isSupportedGpu =
            vendor.contains("nvidia", true) ||
                    renderer.contains("geforce", true) ||
                    renderer.contains("quadro", true) ||
                    vendor.contains("amd", true) ||
                    renderer.contains("radeon", true)
        if (!isSupportedGpu) {
            logger.warn("GPU acceleration only supported on NVIDIA and AMD GPUs")
            logger.info("Falling back to software rendering for browser")
            return false
        }

        // Required OpenGL extensions for D3D11 shared texture interoperability
        // See https://registry.khronos.org/OpenGL/extensions/EXT/EXT_external_objects_win32.txt
        val extensions = arrayOf(
            capabilities.GL_EXT_memory_object,
            capabilities.GL_EXT_memory_object_win32
        )

        logger.info("Checking OpenGL extensions for GPU acceleration" +
                " support: ${extensions.joinToString(", ")}")
        for (extension in extensions) {
            if (!extension) {
                logger.warn("Required OpenGL extension for GPU acceleration not supported")
                logger.info("Falling back to software rendering for browser")
                return false
            }
        }

        true
    } catch (e: Exception) {
        logger.warn("Failed to check GPU acceleration support: ${e.message}")
        logger.info("Falling back to software rendering for browser")
        false
    }
}
