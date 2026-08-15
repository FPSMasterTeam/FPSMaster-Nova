package top.fpsmaster

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
//? if >=1.21.11 {
import net.minecraft.resources.Identifier
//?} else {
/*import net.minecraft.resources.ResourceLocation
*///?}
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import top.fpsmaster.web.BasicBrowser

val logger: Logger = LogManager.getLogger("FPSMaster")

val mc: Minecraft = Minecraft.getInstance()

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

// tryParse exists across all supported versions; only the class name changed (ResourceLocation ->
// Identifier at 1.21.11), so this is a clean 2-way that avoids the constructor-vs-fromNamespaceAndPath
// drift between 1.20.1 (public ctor) and 1.21.x (factory only).
//? if >=1.21.11 {
fun identifier(id: String): Identifier = Identifier.tryParse("fpsmaster:$id")!!
//?} else {
/*fun identifier(id: String): ResourceLocation = ResourceLocation.tryParse("fpsmaster:$id")!!
*///?}

// MC 26.2 relocated screen get/set off Minecraft onto its `gui` field (Minecraft.gui.setScreen(...) /
// Minecraft.gui.screen()). These compat helpers keep the call sites version-agnostic; only this one
// definition is Stonecutter-gated. (Written with the 1.x branch live — the active node is 1.21.11.)
//? if >=26.2 {
/*fun Minecraft.setScreenCompat(screen: net.minecraft.client.gui.screens.Screen?) = this.gui.setScreen(screen)
val Minecraft.screenCompat: net.minecraft.client.gui.screens.Screen? get() = this.gui.screen()
*///?} else {
fun Minecraft.setScreenCompat(screen: net.minecraft.client.gui.screens.Screen?) = this.setScreen(screen)
val Minecraft.screenCompat: net.minecraft.client.gui.screens.Screen? get() = this.screen
//?}

// MC 26.2 removed Options.hideGui (the F1 "hide GUI" state moved). Deferred: default to visible on
// 26.2. This is currently moot because the FPSMaster HUD draw hook (MixinGui) is itself gated off on
// 26.2's rewritten render pipeline; revisit when that hook is ported. [[nova-mc26-unobfuscated-build]]
//? if >=26 {
/*val hideGuiCompat: Boolean get() = false
*///?} else {
val hideGuiCompat: Boolean get() = mc.options.hideGui
//?}