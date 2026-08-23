package top.fpsmaster.ui.kit

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
//? if >=1.21.5 {
import net.minecraft.client.renderer.RenderPipelines
//?}
import top.fpsmaster.render.font.Fonts
import top.fpsmaster.mc
import top.fpsmaster.prism.canvas.Canvas
import top.fpsmaster.prism.canvas.FontHandle
import top.fpsmaster.prism.canvas.ImageHandle
import top.fpsmaster.prism.theme.Argb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class NovaFont(private val font: Font, private val px: Int) : FontHandle {
    /**
     * Edge rasterises TTF at [px] and draws at half size. Shared screens layout against
     * that convention: [lineHeight] = size/2.
     *
     * Nova uses one vanilla TTF atlas ([BASE_SIZE] px, see `assets/fpsmaster/font/ui.json`)
     * and pose-scales it. [yOffset] undoes vanilla's 7px bitmap baseline so [y] is ink-top.
     */
    fun scale(): Float = (px * 0.5f) / BASE_SIZE

    /**
     * Vanilla [GuiGraphics.drawString] y is the top of the 9px bitmap line; glyphs sit on a
     * 7px baseline (`GlyphBitmap.getTop` = 7 − bearing). TTF bearing is ~[INK_ASCENT]×em, so
     * unshifted CJK hangs above [y]. This returns the GUI-space shift that puts ink-top on [y].
     */
    fun yOffset(): Float = (BASE_SIZE * INK_ASCENT - VANILLA_BASELINE) * scale()

    override fun size(): Int = px

    override fun measure(text: String?): Float {
        val value = text ?: return 0f
        return font.width(styled(value)) * scale()
    }

    override fun lineHeight(): Float = px * 0.5f

    fun vanilla(): Font = font

    fun styled(text: String): Component =
        Component.literal(text).withStyle(Style.EMPTY.withFont(Fonts.ui))

    companion object {
        const val BASE_SIZE = 32f
        const val VANILLA_BASELINE = 7f
        const val INK_ASCENT = 0.86f
    }
}

/**
 * Immediate-mode canvas. Solid axis-aligned rects go through [GuiGraphics.fill];
 * round-rects / circles go through [NovaShapeAtlas] (coverage-AA discs at framebuffer
 * density). Scanline rasterisation is only the fallback when the atlas cannot bake.
 */
class NovaCanvas(private val g: GuiGraphics, private val font: Font) : Canvas {
    private val alpha = ArrayDeque<Float>().apply { add(1f) }
    private var clip = 0

    fun graphics(): GuiGraphics = g

    override fun fillRect(x: Float, y: Float, w: Float, h: Float, argb: Int) {
        if (w <= 0f || h <= 0f) {
            return
        }
        rawFill(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt(), tint(argb))
    }

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, argb: Int) {
        if (w <= 0f || h <= 0f) {
            return
        }
        val r = radius.coerceAtMost(min(w, h) * 0.5f).coerceAtLeast(0f)
        if (r < 0.5f) {
            fillRect(x, y, w, h, argb)
            return
        }
        val color = tint(argb)
        if (NovaShapeAtlas.fillRoundRect(g, x, y, w, h, r, color)) {
            return
        }
        val y0 = floor(y).toInt()
        val y1 = ceil(y + h).toInt()
        for (row in y0 until y1) {
            val span = roundSpan(row + 0.5f, x, y, w, h, r) ?: continue
            hline(span.first, span.second, row, color)
        }
    }

    override fun strokeRoundRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        strokeWidth: Float,
        argb: Int
    ) {
        if (w <= 0f || h <= 0f) {
            return
        }
        val s = strokeWidth.coerceAtLeast(0.6f)
        val r = radius.coerceAtMost(min(w, h) * 0.5f).coerceAtLeast(0f)
        val color = tint(argb)
        if (NovaShapeAtlas.strokeRoundRect(g, x, y, w, h, r, s, color)) {
            return
        }
        val y0 = floor(y - s * 0.15f).toInt()
        val y1 = ceil(y + h + s * 0.15f).toInt()
        for (row in y0 until y1) {
            val py = row + 0.5f
            val outer = roundSpan(py, x, y, w, h, r) ?: continue
            val inner = roundSpan(py, x + s, y + s, w - 2f * s, h - 2f * s, (r - s).coerceAtLeast(0f))
            if (inner == null || w <= 2f * s || h <= 2f * s) {
                hline(outer.first, outer.second, row, color)
            } else {
                hline(outer.first, inner.first, row, color)
                hline(inner.second, outer.second, row, color)
            }
        }
    }

    override fun fillCircle(cx: Float, cy: Float, radius: Float, argb: Int) {
        if (radius < 0.4f) {
            rawFill(cx.roundToInt(), cy.roundToInt(), cx.roundToInt() + 1, cy.roundToInt() + 1, tint(argb))
            return
        }
        val color = tint(argb)
        if (NovaShapeAtlas.fillCircle(g, cx, cy, radius, color)) {
            return
        }
        val y0 = floor(cy - radius).toInt()
        val y1 = ceil(cy + radius).toInt()
        val r2 = radius * radius
        for (row in y0 until y1) {
            val dy = (row + 0.5f) - cy
            val inner = r2 - dy * dy
            if (inner <= 0f) {
                continue
            }
            val dx = sqrt(inner)
            hline(cx - dx, cx + dx, row, color)
        }
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, argb: Int) {
        val hw = (width * 0.5f).coerceAtLeast(0.5f)
        val dx = x2 - x1
        val dy = y2 - y1
        if (abs(dx) < 0.35f) {
            fillRect(x1 - hw, min(y1, y2) - hw, hw * 2f, abs(dy) + hw * 2f, argb)
            return
        }
        if (abs(dy) < 0.35f) {
            fillRect(min(x1, x2) - hw, y1 - hw, abs(dx) + hw * 2f, hw * 2f, argb)
            return
        }
        val color = tint(argb)
        val y0 = floor(min(y1, y2) - hw).toInt()
        val y1i = ceil(max(y1, y2) + hw).toInt()
        val len2 = dx * dx + dy * dy
        for (row in y0 until y1i) {
            val py = row + 0.5f
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            val samples = 4
            for (i in 0..samples) {
                val px = min(x1, x2) - hw + (abs(dx) + hw * 2f) * (i / samples.toFloat())
                val t = (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0f, 1f)
                val sx = x1 + t * dx
                val sy = y1 + t * dy
                if (hypot(px - sx, py - sy) <= hw + 0.45f) {
                    minX = min(minX, px)
                    maxX = max(maxX, px)
                }
            }
            val t = (((py - y1) * dy) / len2)
            val closestT = t.coerceIn(0f, 1f)
            val cx = x1 + closestT * dx
            val cy = y1 + closestT * dy
            val dist = abs(py - cy)
            if (dist <= hw + 0.5f) {
                val rest = hw * hw - dist * dist
                if (rest > 0f) {
                    val ox = sqrt(rest)
                    minX = min(minX, cx - ox)
                    maxX = max(maxX, cx + ox)
                }
            }
            if (maxX > minX) {
                hline(minX, maxX, row, color)
            }
        }
    }

    override fun fillGradientH(x: Float, y: Float, w: Float, h: Float, argbLeft: Int, argbRight: Int) {
        g.fillGradient(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt(), tint(argbLeft), tint(argbRight))
    }

    override fun fillGradientV(x: Float, y: Float, w: Float, h: Float, argbTop: Int, argbBottom: Int) {
        g.fillGradient(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt(), tint(argbTop), tint(argbBottom))
    }

    override fun drawString(fontHandle: FontHandle, text: String, x: Float, y: Float, argb: Int) {
        val vanilla = if (fontHandle is NovaFont) fontHandle.vanilla() else font
        val color = tint(argb)
        val scale = if (fontHandle is NovaFont) fontHandle.scale() else 1f
        val pose = g.pose()
        val x0 = snapToFramebuffer(x)
        val y0 = snapToFramebuffer(if (fontHandle is NovaFont) y + fontHandle.yOffset() else y)
        //? if >=1.21.5 {
        pose.pushMatrix()
        pose.translate(x0, y0)
        if (abs(scale - 1f) >= 0.01f) {
            pose.scale(scale, scale)
        }
        if (fontHandle is NovaFont) {
            g.drawString(vanilla, fontHandle.styled(text), 0, 0, color, false)
        } else {
            g.drawString(vanilla, text, 0, 0, color, false)
        }
        pose.popMatrix()
        //?} else {
        /*pose.pushPose()
        pose.translate(x0.toDouble(), y0.toDouble(), 0.0)
        if (abs(scale - 1f) >= 0.01f) {
            pose.scale(scale, scale, 1f)
        }
        if (fontHandle is NovaFont) {
            g.drawString(vanilla, fontHandle.styled(text), 0, 0, color, false)
        } else {
            g.drawString(vanilla, text, 0, 0, color, false)
        }
        pose.popPose()*/
        //?}
    }

    private fun snapToFramebuffer(value: Float): Float {
        val window = mc.window
        val scale = window.width.toFloat() / window.guiScaledWidth.coerceAtLeast(1)
        return (value * scale).roundToInt() / scale
    }

    override fun drawImage(image: ImageHandle, x: Float, y: Float, w: Float, h: Float, tintArgb: Int) {
        if (image !is NovaImage || w <= 0f || h <= 0f) {
            return
        }
        val xi = x.roundToInt()
        val yi = y.roundToInt()
        val wi = w.roundToInt().coerceAtLeast(1)
        val hi = h.roundToInt().coerceAtLeast(1)
        val color = tint(tintArgb)
        val tw = image.width().coerceAtLeast(1)
        val th = image.height().coerceAtLeast(1)
        //? if >=1.21.5 {
        // dest size (wi,hi) is independent of the PNG pixel size (tw,th). The 11-arg blit
        // treats width/height as BOTH dest and UV-region, so a 48px icon drawn at 11px
        // would only sample the top-left 11 texels (a scrap of outline).
        g.blit(
            RenderPipelines.GUI_TEXTURED,
            image.id,
            xi,
            yi,
            0f,
            0f,
            wi,
            hi,
            tw,
            th,
            tw,
            th,
            color
        )
        //?} else {
        /*val a = Argb.alpha(color) / 255f
        val r = Argb.red(color) / 255f
        val gch = Argb.green(color) / 255f
        val b = Argb.blue(color) / 255f
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, gch, b, a)
        val pose = g.pose()
        pose.pushPose()
        pose.translate(xi.toDouble(), yi.toDouble(), 0.0)
        pose.scale(wi.toFloat() / tw, hi.toFloat() / th, 1f)
        g.blit(image.id, 0, 0, 0f, 0f, tw, th, tw, th)
        pose.popPose()
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f)*/
        //?}
    }

    override fun pushClip(x: Float, y: Float, w: Float, h: Float) {
        clip++
        //? if >=1.20 {
        g.enableScissor(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt())
        //?} else {
        /*net.minecraft.client.gui.GuiComponent.enableScissor(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt())*/
        //?}
    }

    override fun popClip() {
        if (clip > 0) {
            clip--
            //? if >=1.20 {
            g.disableScissor()
            //?} else {
            /*net.minecraft.client.gui.GuiComponent.disableScissor()*/
            //?}
        }
    }

    override fun pushAlpha(a: Float) {
        alpha.addLast(alpha.last() * a)
    }

    override fun popAlpha() {
        if (alpha.size > 1) {
            alpha.removeLast()
        }
    }

    override fun pushTransform() {
        //? if >=1.21.5 {
        g.pose().pushMatrix()
        //?} else {
        /*g.pose().pushPose()*/
        //?}
    }

    override fun popTransform() {
        //? if >=1.21.5 {
        g.pose().popMatrix()
        //?} else {
        /*g.pose().popPose()*/
        //?}
    }

    override fun translate(x: Float, y: Float) {
        //? if >=1.21.5 {
        g.pose().translate(x, y)
        //?} else {
        /*g.pose().translate(x.toDouble(), y.toDouble(), 0.0)*/
        //?}
    }

    override fun scale(s: Float) {
        //? if >=1.21.5 {
        g.pose().scale(s, s)
        //?} else {
        /*g.pose().scale(s, s, 1f)*/
        //?}
    }

    private fun roundSpan(py: Float, x: Float, y: Float, w: Float, h: Float, r: Float): Pair<Float, Float>? {
        if (py < y || py >= y + h) {
            return null
        }
        var inset = 0f
        if (r > 0.5f && py < y + r) {
            val dy = r - (py - y)
            val d = r * r - dy * dy
            if (d <= 0f) {
                return null
            }
            inset = r - sqrt(d)
        } else if (r > 0.5f && py > y + h - r) {
            val dy = r - (y + h - py)
            val d = r * r - dy * dy
            if (d <= 0f) {
                return null
            }
            inset = r - sqrt(d)
        }
        return (x + inset) to (x + w - inset)
    }

    private fun hline(x0: Float, x1: Float, y: Int, argb: Int) {
        val a = floor(min(x0, x1)).toInt()
        val b = ceil(max(x0, x1)).toInt()
        if (b > a) {
            rawFill(a, y, b, y + 1, argb)
        }
    }

    private fun rawFill(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int) {
        if (x1 <= x0 || y1 <= y0 || Argb.alpha(argb) <= 2) {
            return
        }
        g.fill(x0, y0, x1, y1, argb)
    }

    private fun tint(argb: Int): Int = Argb.mulAlpha(argb, alpha.last())
}
