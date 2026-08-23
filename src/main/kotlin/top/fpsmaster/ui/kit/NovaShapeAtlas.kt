package top.fpsmaster.ui.kit

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
//? if >=1.21.5 {
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.renderer.RenderPipelines
//?}
import net.minecraft.client.renderer.texture.DynamicTexture
//? if >=1.21.11 {
import net.minecraft.resources.Identifier
//?} else {
/*import net.minecraft.resources.ResourceLocation*/
//?}
import top.fpsmaster.identifier
import top.fpsmaster.mc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cached anti-aliased discs used to draw round-rects / circles at framebuffer density.
 *
 * [NovaCanvas] used to rasterise shapes with one [GuiGraphics.fill] per GUI-unit scanline.
 * Vanilla then scales that by `guiScale` (and Retina), so a 12-tall switch becomes 3–6 px
 * chunks. Baking a white coverage-AA disc at `window.width / guiScaledWidth` and blitting it
 * (tint via vertex colour) maps one texel to one framebuffer pixel.
 */
internal object NovaShapeAtlas {
    private const val MAX_SHAPES = 48
    private const val MAX_PIXEL_RADIUS = 128
    private const val AA_SAMPLES = 4

    fun fillRoundRect(g: GuiGraphics, x: Float, y: Float, w: Float, h: Float, radius: Float, argb: Int): Boolean {
        if (w <= 0f || h <= 0f || argb ushr 24 == 0) {
            return true
        }
        val r = radius.coerceAtMost(min(w, h) * 0.5f).coerceAtLeast(0f)
        if (r < 0.5f) {
            return false
        }
        // A "circle drawn as a round-rect" (switch knobs): one disc blit, no 9-slice seams.
        if (abs(w - h) < 0.51f && abs(w - r * 2f) < 0.51f) {
            return fillCircle(g, x + w * 0.5f, y + h * 0.5f, min(w, h) * 0.5f, argb)
        }
        val shape = disk(r, argb) ?: return false
        val wi = w.roundToInt().coerceAtLeast(1)
        val hi = h.roundToInt().coerceAtLeast(1)
        val maxRadius = min(wi, hi) / 2
        if (maxRadius == 0) {
            return false
        }
        val ri = r.roundToInt().coerceIn(1, maxRadius)
        val pr = shape.pixelRadius
        origin(g, x, y) {
            if (wi - ri * 2 > 0) {
                rawFill(g, ri, 0, wi - ri, ri, argb)
                rawFill(g, ri, hi - ri, wi - ri, hi, argb)
            }
            if (hi - ri * 2 > 0) {
                rawFill(g, 0, ri, ri, hi - ri, argb)
                rawFill(g, wi - ri, ri, wi, hi - ri, argb)
            }
            if (wi - ri * 2 > 0 && hi - ri * 2 > 0) {
                rawFill(g, ri, ri, wi - ri, hi - ri, argb)
            }
            blit(g, shape, 0, 0, ri, ri, 0, 0, pr, pr, argb)
            blit(g, shape, wi - ri, 0, ri, ri, pr, 0, pr, pr, argb)
            blit(g, shape, 0, hi - ri, ri, ri, 0, pr, pr, pr, argb)
            blit(g, shape, wi - ri, hi - ri, ri, ri, pr, pr, pr, pr, argb)
        }
        return true
    }

    fun fillCircle(g: GuiGraphics, cx: Float, cy: Float, radius: Float, argb: Int): Boolean {
        if (radius < 0.4f || argb ushr 24 == 0) {
            return false
        }
        val shape = disk(radius, argb) ?: return false
        val d = (radius * 2f).roundToInt().coerceAtLeast(1)
        origin(g, cx - radius, cy - radius) {
            blit(g, shape, 0, 0, d, d, 0, 0, shape.diameter, shape.diameter, argb)
        }
        return true
    }

    fun strokeRoundRect(
        g: GuiGraphics,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        strokeWidth: Float,
        argb: Int
    ): Boolean {
        if (w <= 0f || h <= 0f || argb ushr 24 == 0) {
            return true
        }
        val s = strokeWidth.coerceAtLeast(0.5f)
        val r = radius.coerceAtMost(min(w, h) * 0.5f).coerceAtLeast(0f)
        if (r < 0.5f) {
            return false
        }
        val inner = (r - s).coerceAtLeast(0f)
        val shape = ring(r, inner, argb) ?: return false
        val wi = w.roundToInt().coerceAtLeast(1)
        val hi = h.roundToInt().coerceAtLeast(1)
        val maxRadius = min(wi, hi) / 2
        if (maxRadius == 0) {
            return false
        }
        val ri = r.roundToInt().coerceIn(1, maxRadius)
        val si = s.roundToInt().coerceIn(1, ri)
        val pr = shape.pixelRadius
        origin(g, x, y) {
            if (wi - ri * 2 > 0) {
                rawFill(g, ri, 0, wi - ri, si, argb)
                rawFill(g, ri, hi - si, wi - ri, hi, argb)
            }
            if (hi - ri * 2 > 0) {
                rawFill(g, 0, ri, si, hi - ri, argb)
                rawFill(g, wi - si, ri, wi, hi - ri, argb)
            }
            blit(g, shape, 0, 0, ri, ri, 0, 0, pr, pr, argb)
            blit(g, shape, wi - ri, 0, ri, ri, pr, 0, pr, pr, argb)
            blit(g, shape, 0, hi - ri, ri, ri, 0, pr, pr, pr, argb)
            blit(g, shape, wi - ri, hi - ri, ri, ri, pr, pr, pr, pr, argb)
        }
        return true
    }

    private val cache = object : LinkedHashMap<ShapeKey, Shape>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ShapeKey, Shape>?): Boolean {
            if (size <= MAX_SHAPES) {
                return false
            }
            eldest?.value?.close()
            return true
        }
    }

    private fun disk(guiRadius: Float, argb: Int): Shape? = shape(pixelRadius(guiRadius), 0, argb)

    private fun ring(guiOuter: Float, guiInner: Float, argb: Int): Shape? {
        val outer = pixelRadius(guiOuter)
        val inner = if (guiInner <= 0f) 0 else pixelRadius(guiInner).coerceAtMost(outer - 1)
        return shape(outer, inner, argb)
    }

    private fun shape(pixelRadius: Int, innerPixelRadius: Int, argb: Int): Shape? {
        //? if >=1.21.5 {
        val bakedColor = 0
        //?} else {
        /*val bakedColor = argb*/
        //?}
        val key = ShapeKey(pixelRadius, innerPixelRadius, bakedColor)
        cache[key]?.let { return it }
        val baked = bake(pixelRadius, innerPixelRadius, bakedColor) ?: return null
        cache[key] = baked
        return baked
    }

    private fun pixelRadius(guiRadius: Float): Int {
        val scale = fbScale()
        return (guiRadius * scale).roundToInt().coerceIn(1, MAX_PIXEL_RADIUS)
    }

    private fun fbScale(): Float {
        val window = mc.window
        val gui = window.guiScaledWidth.coerceAtLeast(1)
        return (window.width.toFloat() / gui).coerceAtLeast(1f)
    }

    private fun bake(pixelRadius: Int, innerPixelRadius: Int, argb: Int): Shape? {
        val diameter = pixelRadius * 2
        val image = NativeImage(diameter, diameter, false)
        val cx = diameter * 0.5
        val cy = diameter * 0.5
        val outer = pixelRadius.toDouble()
        val inner = innerPixelRadius.toDouble()
        val samples = AA_SAMPLES
        val inv = 1.0 / samples
        val area = samples * samples
        for (py in 0 until diameter) {
            for (px in 0 until diameter) {
                var hit = 0
                var sy = 0
                while (sy < samples) {
                    var sx = 0
                    while (sx < samples) {
                        val x = px + (sx + 0.5) * inv
                        val y = py + (sy + 0.5) * inv
                        val d = hypot(x - cx, y - cy)
                        if (d <= outer && d >= inner) {
                            hit++
                        }
                        sx++
                    }
                    sy++
                }
                val alpha = (hit * 255) / area
                //? if >=1.21.5 {
                image.setPixelABGR(px, py, (alpha shl 24) or 0x00FFFFFF)
                //?} else {
                /*val coverageAlpha = alpha * ((argb ushr 24) and 0xFF) / 255
                val abgr = (coverageAlpha shl 24) or
                    ((argb and 0xFF) shl 16) or
                    (argb and 0x00FF00) or
                    ((argb ushr 16) and 0xFF)
                image.setPixelRGBA(px, py, abgr)*/
                //?}
            }
        }
        val suffix = if (argb == 0) "$pixelRadius-$innerPixelRadius" else "$pixelRadius-$innerPixelRadius-${argb.toUInt()}"
        val name = "fpsmaster-ui-shape-$suffix"
        val id = identifier("ui/shape/$suffix")
        val texture = ShapeTexture(image, name)
        mc.textureManager.register(id, texture)
        return Shape(id, pixelRadius, diameter)
    }

    private fun blit(
        g: GuiGraphics,
        shape: Shape,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        u: Int,
        v: Int,
        uw: Int,
        uh: Int,
        argb: Int
    ) {
        if (w <= 0 || h <= 0) {
            return
        }
        //? if >=1.21.5 {
        g.blit(
            RenderPipelines.GUI_TEXTURED,
            shape.id,
            x,
            y,
            u.toFloat(),
            v.toFloat(),
            w,
            h,
            uw,
            uh,
            shape.diameter,
            shape.diameter,
            argb
        )
        //?} else {
        /*val pose = g.pose()
        pose.pushPose()
        pose.translate(x.toDouble(), y.toDouble(), 0.0)
        pose.scale(w.toFloat() / uw, h.toFloat() / uh, 1f)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        g.blit(shape.id, 0, 0, u.toFloat(), v.toFloat(), uw, uh, shape.diameter, shape.diameter)
        pose.popPose()*/
        //?}
    }

    private fun rawFill(g: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, argb: Int) {
        if (x1 > x0 && y1 > y0) {
            g.fill(x0, y0, x1, y1, argb)
        }
    }

    private inline fun origin(g: GuiGraphics, x: Float, y: Float, block: () -> Unit) {
        val pose = g.pose()
        //? if >=1.21.5 {
        pose.pushMatrix()
        pose.translate(x, y)
        block()
        pose.popMatrix()
        //?} else {
        /*pose.pushPose()
        pose.translate(x.toDouble(), y.toDouble(), 0.0)
        block()
        pose.popPose()*/
        //?}
    }

    private class Shape(
        //? if >=1.21.11 {
        val id: Identifier,
        //?} else {
        /*val id: ResourceLocation,*/
        //?}
        val pixelRadius: Int,
        val diameter: Int
    ) {
        fun close() {
            mc.textureManager.release(id)
        }
    }

    private data class ShapeKey(val pixelRadius: Int, val innerPixelRadius: Int, val argb: Int)

    /**
     * DynamicTexture defaults to NEAREST + REPEAT. Repeat wraps the opposite side of the disc into
     * the outer edge; nearest would reintroduce the GUI-pixel stair-step when dest size is not an
     * exact texel multiple (sub-pixel knob motion). Linear clamp matches the coverage-AA disc.
     */
    private class ShapeTexture(
        image: NativeImage,
        name: String
    ) : DynamicTexture(
        //? if >=1.21.5 {
        { name }, image
        //?} else {
        /*image*/
        //?}
    ) {
        init {
            //? if >=1.21.11 {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false)
            //?} else if >=1.21.5 {
            /*setFilter(true, false)
            setClamp(true)*/
            //?} else {
            /*setFilter(true, false)*/
            //?}
        }
    }
}
