package top.fpsmaster.hud.impl

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.PerformanceHud
import top.fpsmaster.statistics.PerformanceMonitor
import kotlin.math.roundToInt

/**
 * The performance overlay's drawing half: a frame-time trace over a few rows of statistics.
 *
 * The trace is scaled so the 16.7ms line (60fps) sits at a fixed height, and columns that exceed the
 * window are drawn clipped and red rather than rescaling the whole graph — a graph that renormalises
 * on every spike makes a stutter look identical to a smooth run.
 */
class PerformanceHudComponent : HudComponent(
    id = "performance",
    x = 10f,
    y = 230f
) {
    private val trace = FloatArray(PerformanceMonitor.columns)

    override fun shouldRender(): Boolean = visible && PerformanceHud.isActive()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        var width = if (PerformanceHud.showGraph.getValue()) GRAPH_WIDTH else 0
        var height = if (PerformanceHud.showGraph.getValue()) GRAPH_HEIGHT + ROW_GAP else 0
        lines(preview).forEach { line ->
            width = maxOf(width, mc.font.width(line))
            height += mc.font.lineHeight
        }
        return HudSize(width.toFloat().coerceAtLeast(1f), height.toFloat().coerceAtLeast(1f))
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        if (!preview) {
            PerformanceMonitor.onFrame()
        }

        val size = measure(preview)
        PerformanceHud.style.fillBackground(guiGraphics, -2, -1, size.width.toInt() + 2, size.height.toInt() + 1)

        var y = 0
        if (PerformanceHud.showGraph.getValue()) {
            drawGraph(guiGraphics, y, preview)
            y += GRAPH_HEIGHT + ROW_GAP
        }

        val frames = if (preview) PREVIEW_FPS else PerformanceMonitor.fps
        lines(preview).forEachIndexed { index, line ->
            val color = if (index == 0) PerformanceHud.healthColor(frames) else PerformanceHud.textColorValue()
            guiGraphics.drawString(
                mc.font,
                PerformanceHud.style.component(line),
                0,
                y,
                color,
                PerformanceHud.style.fontShadow.getValue()
            )
            y += mc.font.lineHeight
        }
    }

    private fun drawGraph(guiGraphics: GuiGraphics, top: Int, preview: Boolean) {
        guiGraphics.fill(0, top, GRAPH_WIDTH, top + GRAPH_HEIGHT, GRAPH_BACKGROUND)
        if (preview) {
            PREVIEW_TRACE.indices.forEach { index ->
                drawColumn(guiGraphics, top, index, PREVIEW_TRACE[index])
            }
        } else {
            PerformanceMonitor.traceInto(trace)
            trace.indices.forEach { index -> drawColumn(guiGraphics, top, index, trace[index]) }
        }

        // The 60fps reference line, so the trace reads without a scale printed beside it.
        val referenceY = top + GRAPH_HEIGHT - (GRAPH_HEIGHT * (REFERENCE_MS / GRAPH_CEILING_MS)).roundToInt()
        guiGraphics.fill(0, referenceY, GRAPH_WIDTH, referenceY + 1, REFERENCE_COLOR)
    }

    private fun drawColumn(guiGraphics: GuiGraphics, top: Int, index: Int, frameMs: Float) {
        if (frameMs <= 0f) {
            return
        }
        val clipped = frameMs > GRAPH_CEILING_MS
        val height = ((frameMs / GRAPH_CEILING_MS) * GRAPH_HEIGHT).roundToInt().coerceIn(1, GRAPH_HEIGHT)
        val color = when {
            clipped -> 0xFFE76F51.toInt()
            frameMs > REFERENCE_MS -> 0xFFE9C46A.toInt()
            else -> 0xFF5BD97F.toInt()
        }
        val left = index * COLUMN_WIDTH
        guiGraphics.fill(left, top + GRAPH_HEIGHT - height, left + COLUMN_WIDTH, top + GRAPH_HEIGHT, color)
    }

    private fun lines(preview: Boolean): List<String> {
        if (preview) {
            return buildList {
                add("143 fps")
                if (PerformanceHud.showDistribution.getValue()) add("1% low 74  p50 6.8ms  max 41.2ms  hitch 3")
                if (PerformanceHud.showMemory.getValue()) add("heap 1240/4096 MB  alloc 42.0 MB/s")
                if (PerformanceHud.showGarbageCollection.getValue()) add("gc 1.2/s  3.4 ms/s")
            }
        }

        return buildList {
            add("${PerformanceMonitor.fps.roundToInt()} fps  avg ${PerformanceMonitor.averageFps.roundToInt()}")
            if (PerformanceHud.showDistribution.getValue()) {
                add(
                    "1% low ${PerformanceMonitor.onePercentLowFps.roundToInt()}" +
                        "  p50 ${format(PerformanceMonitor.medianFrameMs)}ms" +
                        "  max ${format(PerformanceMonitor.worstFrameMs)}ms" +
                        "  hitch ${PerformanceMonitor.hitches}"
                )
            }
            if (PerformanceHud.showMemory.getValue()) {
                val heap = "heap ${PerformanceMonitor.heapUsedMb}/${PerformanceMonitor.heapMaxMb} MB"
                add(
                    if (PerformanceMonitor.allocatedMbPerSecond > 0.0) {
                        "$heap  alloc ${format(PerformanceMonitor.allocatedMbPerSecond)} MB/s"
                    } else {
                        heap
                    }
                )
            }
            if (PerformanceHud.showGarbageCollection.getValue()) {
                add(
                    "gc ${format(PerformanceMonitor.gcPerSecond)}/s" +
                        "  ${format(PerformanceMonitor.gcMillisPerSecond)} ms/s"
                )
            }
        }
    }

    private fun format(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()

    companion object {
        private const val COLUMN_WIDTH = 1
        private const val GRAPH_HEIGHT = 32
        private const val ROW_GAP = 2

        /** Frames slower than this are drawn clipped rather than rescaling the graph. */
        private const val GRAPH_CEILING_MS = 50f
        private const val REFERENCE_MS = 1000f / 60f
        private const val REFERENCE_COLOR = 0x55FFFFFF
        private const val GRAPH_BACKGROUND = 0x66000000
        private const val PREVIEW_FPS = 143.0

        private val GRAPH_WIDTH = PerformanceMonitor.columns * COLUMN_WIDTH

        /** A plausible-looking trace for the HUD editor, where no frames are being sampled. */
        private val PREVIEW_TRACE = FloatArray(PerformanceMonitor.columns) { index ->
            when {
                index % 37 == 0 -> 28f
                index % 11 == 0 -> 12f
                else -> 6.5f + (index % 5) * 0.4f
            }
        }
    }
}
