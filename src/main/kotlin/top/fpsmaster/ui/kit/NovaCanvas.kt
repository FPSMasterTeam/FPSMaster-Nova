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
import top.fpsmaster.cosmetic.TextureId
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
     * Edge 把 TTF 按 [px] 栅格化、再以半尺寸绘制，所以在共享布局里「字号 px」的含义是
     * **em 高度等于 `px / 2` 个 GUI 像素**，[lineHeight] 也正好是这个数。Nova 只有一张
     * 原版 TTF 图集（`assets/fpsmaster/font/ui.json`，`size: 32`、`oversample: 4`），
     * 靠 pose 缩放去凑这个 em——[EM_SIZE] 就是「一个 em 折算成多少 vanilla 单位」。
     *
     * 注意 [EM_SIZE] 是**分代**的，因为 `TrueTypeGlyphProvider` 在 1.21 换了字形后端：
     * - 1.21 之前用 `stbtt_ScaleForPixelHeight(font, size * oversample)`，它把
     *   `ascender - descender` 映射成 `size * oversample` 像素。notosans_sc.ttf 的 hhea 是
     *   `1160 / -288`、`unitsPerEm = 1000`，一个 em 只剩 `32 * 1000 / 1448 ≈ 22.1` 单位。
     * - 1.21 起改用 FreeType `FT_Set_Pixel_Sizes(face, n, n)`（`n = round(size * oversample)`，
     *   宽高传的是同一个非零值），它的语义是把 `unitsPerEm` 映射到请求的像素高，
     *   所以一个 em 就是 `size = 32` 单位。
     *
     * 拿错一档，字号会整体差 `1448 / 1000 = 1.448` 倍。
     */
    fun scale(): Float = lineHeight() / EM_SIZE

    /**
     * 共享布局（`Chrome.textY`）给的 y 是一个 [lineHeight] 高行盒的顶边，期望墨迹在盒内居中；
     * 而 `Font.drawInBatch` 的 y 是「默认字形的墨迹顶」，离基线 [BASELINE_V] 个 vanilla 单位。
     * 这个距离同样分代，且都不是 ascender 线：
     * - 1.21 之前 `SheetGlyphInfo.getUp() = getBearingY()`（TTF 字形量的是「ascender 线到墨迹顶」），
     *   而 `BakedGlyph.render` 统一再减 3——默认 `getBearingY()` 就是 3f，所以 ascender 线落在
     *   `y - 3`，基线在 `y + ASCENDER * EM_SIZE - 3`。
     * - 1.21 起 `SheetGlyphInfo.getTop() = 7f - getBearingTop()`（该接口 1.21.11 起改名
     *   `GlyphBitmap`，字节码一致），`getBearingTop()` 是 `bitmap_top / oversample`
     *   （基线到墨迹顶），基线固定落在 `y + 7`，与字体度量无关。
     *
     * 汉字的墨迹带（OS/2 typo `880 / -120`）中心在基线上方 [INK_CENTER] 个 em（拉丁大写
     * capHeight = 733，中心 0.37，几乎同一条线）。要让墨迹中心压在行盒中心
     * `y + lineHeight / 2`，基线就得落在 `y + (0.5 + INK_CENTER) * em`，这里补的是两者之差。
     */
    fun yOffset(): Float = (0.5f + INK_CENTER) * lineHeight() - BASELINE_V * scale()

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
        /** `ui.json` 里的 `size`。 */
        const val ATLAS_SIZE = 32f

        /** OS/2 typo 升降部中点 `(880 - 120) / 2 / unitsPerEm`，单位是 em。 */
        const val INK_CENTER = 0.380f

        //? if <1.21 {
        /*// hhea `ascender / unitsPerEm`，单位是 em。只有 STB 那档用得上。
        const val ASCENDER = 1.160f

        // STB：`ATLAS_SIZE * unitsPerEm / (ascender - descender)`。
        const val EM_SIZE = ATLAS_SIZE * 1000f / 1448f

        // STB：`BakedGlyph` 统一减 3，所以 ascender 线在 `y - 3`。
        const val BASELINE_V = ASCENDER * EM_SIZE - 3f*/
        //?} else {
        /** FreeType：`FT_Set_Pixel_Sizes` 把 unitsPerEm 映射到像素高，em 就是 `size`。 */
        const val EM_SIZE = ATLAS_SIZE

        /** FreeType：`SheetGlyphInfo`/`GlyphBitmap` 的 `getTop() = 7f - bearingTop`，基线恒在 `y + 7`。 */
        const val BASELINE_V = 7f
        //?}
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

    /**
     * 当前生效的裁剪矩形栈，元素是 `[x, y, w, h]`。入栈的矩形已由 `UiFrame.pushClip`
     * 与父矩形求过交，这里直接存。
     *
     * 两个用途：
     * 1. 1.20 之前 `GuiComponent` 没有 ScissorStack——`disableScissor` 就是一句
     *    `RenderSystem.disableScissor()`，pop 等于把裁剪整个关掉而不是回到父矩形，
     *    所以得自己把父矩形装回去（栈空才真正关掉）。
     * 2. 延迟绘制的东西（饰品界面的翅膀 / 披风缩略图是在 `Screen.render` 里补画的，
     *    那时 clip 栈早就退干净了）需要知道「当初画这张卡时的裁剪是什么」，
     *    否则滚出可视区的卡片照样把模型画到列表外面去。见 [currentClip]。
     */
    private val clipStack = ArrayDeque<FloatArray>()

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
        com.mojang.blaze3d.systems.RenderSystem.enableBlend()
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc()
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

    /**
     * Stretches the whole captured frame over the GUI viewport. Callers scissor first; the shape of
     * a panel's backdrop comes from the clip stack, not from this blit.
     */
    fun drawBackdrop(id: TextureId, texWidth: Int, texHeight: Int) {
        if (texWidth <= 0 || texHeight <= 0) {
            return
        }
        val window = mc.window
        val guiWidth = window.guiScaledWidth
        val guiHeight = window.guiScaledHeight
        //? if >=1.21.5 {
        g.blit(
            RenderPipelines.GUI_TEXTURED,
            id,
            0,
            0,
            0f,
            0f,
            guiWidth,
            guiHeight,
            texWidth,
            texHeight,
            texWidth,
            texHeight,
            -1
        )
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.disableBlend()
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        val pose = g.pose()
        pose.pushPose()
        pose.scale(guiWidth.toFloat() / texWidth, guiHeight.toFloat() / texHeight, 1f)
        g.blit(id, 0, 0, 0f, 0f, texWidth, texHeight, texWidth, texHeight)
        pose.popPose()
        com.mojang.blaze3d.systems.RenderSystem.enableBlend()
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc()*/
        //?}
    }

    override fun pushClip(x: Float, y: Float, w: Float, h: Float) {
        clip++
        clipStack.addLast(floatArrayOf(x, y, w, h))
        //? if >=1.20 {
        g.enableScissor(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt())
        //?} else {
        /*net.minecraft.client.gui.GuiComponent.enableScissor(
            x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt()
        )*/
        //?}
    }

    override fun popClip() {
        if (clip > 0) {
            clip--
            if (clipStack.isNotEmpty()) {
                clipStack.removeLast()
            }
            //? if >=1.20 {
            g.disableScissor()
            //?} else {
            /*val parent = clipStack.lastOrNull()
            if (parent == null) {
                net.minecraft.client.gui.GuiComponent.disableScissor()
            } else {
                net.minecraft.client.gui.GuiComponent.enableScissor(
                    parent[0].roundToInt(), parent[1].roundToInt(),
                    (parent[0] + parent[2]).roundToInt(), (parent[1] + parent[3]).roundToInt()
                )
            }*/
            //?}
        }
    }

    /**
     * 当前生效的裁剪矩形 `[x, y, w, h]`，没有裁剪时返回 null。
     *
     * 给「画的时候不在 clip 栈里」的调用方用：在 paint 阶段把这个矩形记下来，
     * 补画时再自己开一次 scissor。返回的是栈里那份实例的副本，调用方改不坏栈。
     */
    fun currentClip(): FloatArray? = clipStack.lastOrNull()?.copyOf()

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
