package top.fpsmaster.ui

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.identifier
import top.fpsmaster.mc
import java.awt.Color
import java.nio.file.Files
import kotlin.math.max
import kotlin.math.roundToInt

object MainMenuBackgroundRenderer {
    private var customTexture: DynamicTexture? = null
    private var customLastModified = -1L
    private var customWidth = 1
    private var customHeight = 1

    @JvmStatic
    fun shouldUseVanillaPanorama(): Boolean {
        val background = ConfigManager.background
        return background.isBlank() || background == "panorama"
    }

    @JvmStatic
    fun render(guiGraphics: GuiGraphics, width: Int, height: Int, partialTick: Float) {
        val background = ConfigManager.background
        when {
            background == "classic" -> guiGraphics.fill(0, 0, width, height, classicColor())
            background == "shader" -> renderShader(guiGraphics, width, height)
            background == "custom" -> renderCustom(guiGraphics, width, height)
            isPanorama(background) -> renderPanoramaImage(guiGraphics, width, height, normalizePanorama(background))
            else -> guiGraphics.fillGradient(0, 0, width, height, 0xFF20242E.toInt(), 0xFF10131A.toInt())
        }
    }

    @JvmStatic
    fun customBackgroundFile() = mc.gameDirectory.toPath().resolve("fpsmaster").resolve("background.png")

    @JvmStatic
    fun hasCustomImage(): Boolean = Files.isRegularFile(customBackgroundFile())

    @JvmStatic
    fun prepareCustom(): Boolean {
        if (!hasCustomImage()) {
            return false
        }
        ensureCustomTexture()
        return customTexture != null
    }

    @JvmStatic
    fun customTextureWidth(): Int = customWidth

    @JvmStatic
    fun customTextureHeight(): Int = customHeight

    @JvmStatic
    fun customTextureId() = customId()

    @JvmStatic
    fun blitPreview(guiGraphics: GuiGraphics, id: String, x: Int, y: Int, w: Int, h: Int) {
        when {
            id == "classic" -> guiGraphics.fill(x, y, x + w, y + h, classicColor())
            id == "shader" -> {
                guiGraphics.fillGradient(x, y, x + w, y + h, 0xFF1A3A6E.toInt(), 0xFF264D8C.toInt())
            }
            id == "custom" && hasCustomImage() -> {
                ensureCustomTexture()
                blitCover(guiGraphics, customId(), customWidth, customHeight, x, y, w, h)
            }
            isPanorama(id) -> blitCover(
                guiGraphics,
                panoramaId(normalizePanorama(id)),
                512,
                512,
                x,
                y,
                w,
                h
            )
            else -> guiGraphics.fill(x, y, x + w, y + h, 0xFF151A20.toInt())
        }
    }

    private fun renderPanoramaImage(guiGraphics: GuiGraphics, width: Int, height: Int, style: String) {
        blitCover(guiGraphics, panoramaId(style), 512, 512, 0, 0, width, height)
        guiGraphics.fill(0, 0, width, height, 0x1E161616)
    }

    private fun renderCustom(guiGraphics: GuiGraphics, width: Int, height: Int) {
        if (!hasCustomImage()) {
            guiGraphics.fill(0, 0, width, height, 0xFF05060A.toInt())
            return
        }
        ensureCustomTexture()
        blitCover(guiGraphics, customId(), customWidth, customHeight, 0, 0, width, height)
        guiGraphics.fill(0, 0, width, height, 0x3A000000)
    }

    private fun ensureCustomTexture() {
        val file = customBackgroundFile().toFile()
        val modified = file.lastModified()
        if (customTexture != null && customLastModified == modified) {
            return
        }
        try {
            Files.newInputStream(file.toPath()).use { stream ->
                val image = NativeImage.read(stream)
                customWidth = image.width
                customHeight = image.height
                customTexture?.close()
                //? if >=1.21.5 {
                val texture = DynamicTexture({ "fpsmaster-custom-bg" }, image)
                //?} else {
                /*val texture = DynamicTexture(image)*/
                //?}
                mc.textureManager.register(customId(), texture)
                customTexture = texture
                customLastModified = modified
            }
        } catch (_: Exception) {
            customTexture = null
            customLastModified = modified
        }
    }

    private fun blitCover(
        guiGraphics: GuiGraphics,
        id: Any,
        texW: Int,
        texH: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ) {
        if (texW <= 0 || texH <= 0 || w <= 0 || h <= 0) {
            return
        }
        val scale = max(w.toFloat() / texW, h.toFloat() / texH)
        val dw = (texW * scale).roundToInt().coerceAtLeast(1)
        val dh = (texH * scale).roundToInt().coerceAtLeast(1)
        val dx = x + (w - dw) / 2
        val dy = y + (h - dh) / 2
        guiGraphics.enableScissor(x, y, x + w, y + h)
        //? if >=1.21.5 {
        @Suppress("UNCHECKED_CAST")
        guiGraphics.blit(
            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            id as net.minecraft.resources.Identifier,
            dx,
            dy,
            0f,
            0f,
            dw,
            dh,
            texW,
            texH,
            texW,
            texH
        )
        //?} else {
        /*@Suppress("UNCHECKED_CAST")
        guiGraphics.blit(id as net.minecraft.resources.ResourceLocation, dx, dy, 0f, 0f, dw, dh, texW, texH)*/
        //?}
        guiGraphics.disableScissor()
    }

    private fun classicColor(): Int {
        var hue = ConfigManager.classicBackgroundHue.coerceIn(0f, 1f)
        val saturation = ConfigManager.classicBackgroundSaturation.coerceIn(0f, 1f)
        val brightness = ConfigManager.classicBackgroundBrightness.coerceIn(0f, 1f)
        var alpha = ConfigManager.classicBackgroundAlpha.coerceIn(0f, 1f)
        val mode = ConfigManager.classicBackgroundMode
        val now = System.nanoTime()
        val dynamicHue = ((now / 1_000_000_000.0 / 6.0) % 1.0).toFloat()
        when {
            mode.equals("WAVE", true) ->
                alpha *= (0.35f + 0.65f * ((Math.sin(now / 450_000_000.0) + 1.0) * 0.5).toFloat())
            mode.equals("CHROMA", true) -> hue = (hue + dynamicHue) % 1f
            mode.equals("RAINBOW", true) -> hue = dynamicHue
        }
        val rgb = Color.HSBtoRGB(hue, saturation, brightness) and 0x00FFFFFF
        return ((alpha * 255f).toInt() shl 24) or rgb
    }

    private fun renderShader(guiGraphics: GuiGraphics, width: Int, height: Int) {
        val now = System.nanoTime()
        val phase = ((now / 1_000_000_000.0) % 8.0 / 8.0).toFloat()
        val top = argb(255, 20 + (20 * phase).toInt(), 35, 78 + (48 * phase).toInt())
        val bottom = argb(255, 8, 12 + (34 * phase).toInt(), 32 + (60 * (1f - phase)).toInt())
        guiGraphics.fillGradient(0, 0, width, height, top, bottom)
        guiGraphics.fill(0, 0, width, height / 3, 0x24000000)
    }

    private fun isPanorama(value: String) = value == "panorama" || value.startsWith("panorama_")

    private fun normalizePanorama(value: String) =
        if (value == "panorama" || value.isBlank()) "panorama_1" else value

    private fun panoramaId(style: String) = identifier("textures/gui/title/background/${style}_0.png")

    private fun customId() = identifier("gui/background_custom")

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        fun c(v: Int) = v.coerceIn(0, 255)
        return (c(alpha) shl 24) or (c(red) shl 16) or (c(green) shl 8) or c(blue)
    }
}
