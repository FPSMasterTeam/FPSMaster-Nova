package top.fpsmaster.ui.kit

import top.fpsmaster.identifier
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.prism.theme.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Panel backdrops for every Prism surface.
 *
 * [NovaBlurCapture] blurs the frame once, the first time a panel asks for it; each panel then
 * composites that one texture through its own rounded mask. The mask is scissor geometry rather than
 * a second sampler because the composite has to work on both the immediate-mode (<1.21.5) and the
 * pipeline (>=1.21.5) GUI stacks, and only rectangles clip identically on both.
 *
 * When the capture is impossible the effect turns itself off for the session and panels are filled
 * with the theme's opaque base, so a panel is never left see-through.
 */
object NovaBlur {
    /** Scissor rows per corner. Six keeps the staircase under the panel's own anti-aliased stroke. */
    private const val CORNER_ROWS = 6

    private val captureId = identifier("nova_blur_capture")

    private var captured = false

    /** Every Prism surface opens a frame; the next panel re-captures what is beneath it. */
    fun beginFrame() {
        captured = false
    }

    fun enabled(): Boolean = ClientSettings.blur.getValue() && !NovaBlurCapture.isUnsupported()

    fun release() {
        NovaBlurCapture.release()
        captured = false
    }

    fun behind(canvas: NovaCanvas, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        if (w <= 0f || h <= 0f) {
            return
        }
        if (!captured) {
            captured = NovaBlurCapture.capture()
        }
        if (!captured) {
            canvas.fillRoundRect(x, y, w, h, radius, solidBase())
            return
        }
        val texWidth = NovaBlurCapture.textureWidth()
        val texHeight = NovaBlurCapture.textureHeight()
        val corner = radius.coerceIn(0f, min(w, h) * 0.5f)
        if (corner <= 0.5f) {
            band(canvas, texWidth, texHeight, x, y, w, h)
            return
        }
        band(canvas, texWidth, texHeight, x, y + corner, w, h - 2f * corner)
        val row = corner / CORNER_ROWS
        for (i in 0 until CORNER_ROWS) {
            // Distance from the corner arc's centre row to this row's centre, so the inset follows
            // the arc instead of the bounding box.
            val fromCentre = corner - (i + 0.5f) * row
            val halfSpan = sqrt(max(0f, corner * corner - fromCentre * fromCentre))
            val inset = corner - halfSpan
            val bandWidth = w - 2f * inset
            band(canvas, texWidth, texHeight, x + inset, y + i * row, bandWidth, row)
            band(canvas, texWidth, texHeight, x + inset, y + h - (i + 1) * row, bandWidth, row)
        }
    }

    private fun band(
        canvas: NovaCanvas,
        texWidth: Int,
        texHeight: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (w <= 0f || h <= 0f) {
            return
        }
        canvas.pushClip(x, y, w, h)
        try {
            canvas.drawBackdrop(captureId, texWidth, texHeight)
        } finally {
            canvas.popClip()
        }
    }

    private fun solidBase(): Int {
        return Theme.of(ClientSettings.lightTheme(), false).opaquePanelBase()
    }
}
