package top.fpsmaster.ui.kit

import net.minecraft.client.gui.Font
import top.fpsmaster.identifier
import top.fpsmaster.mc
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.uikit.canvas.Canvas
import top.fpsmaster.uikit.canvas.FontHandle
import top.fpsmaster.uikit.canvas.ImageHandle
import top.fpsmaster.uikit.host.UiHost
import top.fpsmaster.uikit.input.FrameInput
import top.fpsmaster.uikit.input.Input

class NovaHost(
    private val canvasImpl: NovaCanvas,
    private val inputImpl: FrameInput,
    private val font: Font,
    private val w: Float,
    private val h: Float
) : UiHost {
    private val images = HashMap<String, NovaImage>()

    override fun canvas(): Canvas = canvasImpl
    override fun input(): Input = inputImpl
    override fun font(size: Int): FontHandle = NovaFont(font, size)
    override fun width(): Float = w
    override fun height(): Float = h
    override fun nowNanos(): Long = System.nanoTime()
    override fun blurEnabled(): Boolean = ClientSettings.blur.getValue()
    override fun blurBehind(x: Float, y: Float, w: Float, h: Float, radius: Float) {}

    override fun image(id: String): ImageHandle? = image(id, 11f)

    override fun image(id: String, drawSize: Float): ImageHandle? {
        if (id.isEmpty()) {
            return null
        }
        val bucket = pixelBucket(drawSize)
        val key = "$bucket/$id"
        return images.getOrPut(key) {
            NovaImage(identifier("textures/gui/icons/$key.png"), bucket, bucket)
        }
    }

    private fun pixelBucket(drawSize: Float): Int {
        val window = mc.window
        val gui = window.guiScaledWidth.coerceAtLeast(1)
        val device = drawSize * (window.width.toFloat() / gui)
        return when {
            device <= 24f -> 24
            device <= 48f -> 48
            else -> 96
        }
    }
}
