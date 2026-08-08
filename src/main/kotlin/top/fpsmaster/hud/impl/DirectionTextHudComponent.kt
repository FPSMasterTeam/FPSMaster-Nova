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
import top.fpsmaster.module.impl.ui.DirectionDisplay
import kotlin.math.abs

class DirectionTextHudComponent : HudComponent(
    id = "direction_text",
    x = 10f,
    y = 25f
) {
    override fun shouldRender(): Boolean = visible && DirectionDisplay.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize = HudSize(width = WIDTH.toFloat(), height = HEIGHT.toFloat())

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val yaw = if (preview) 0f else mc.player?.yRot ?: return
        val centerX = WIDTH / 2f
        val offset = ((yaw % 360f + 360f) % 360f) / 360f * FULL_WIDTH

        //? if >=1.20 {
        guiGraphics.enableScissor(0, 0, WIDTH, HEIGHT)
        //?} else {
        /*net.minecraft.client.gui.GuiComponent.enableScissor(0, 0, WIDTH, HEIGHT)*/
        //?}
        for (repeat in -1..1) {
            MARKERS.forEachIndexed { index, marker ->
                val x = centerX + repeat * FULL_WIDTH + index * MARKER_SPACING - offset
                if (x < -MARKER_SPACING || x > WIDTH + MARKER_SPACING) {
                    return@forEachIndexed
                }

                val alpha = alphaAt(x)
                if (alpha <= 3) {
                    return@forEachIndexed
                }

                val color = (alpha shl 24) or 0x00FFFFFF
                if (marker.type == MarkerType.TICK) {
                    guiGraphics.fill(x.toInt(), 4, x.toInt() + 1, 10, color)
                    guiGraphics.drawString(mc.font, marker.text, (x - mc.font.width(marker.text) / 2f).toInt(), 13, color, false)
                } else {
                    guiGraphics.drawString(
                        mc.font,
                        marker.text,
                        (x - mc.font.width(marker.text) / 2f).toInt(),
                        if (marker.type == MarkerType.CARDINAL) 2 else 5,
                        color,
                        false
                    )
                }
            }
        }
        //? if >=1.20 {
        guiGraphics.disableScissor()
        //?} else {
        /*net.minecraft.client.gui.GuiComponent.disableScissor()*/
        //?}
    }

    private fun alphaAt(x: Float): Int {
        val distance = abs(WIDTH / 2f - x)
        return (255f - distance * 2.55f).toInt().coerceIn(0, 255)
    }

    private data class Marker(val text: String, val type: MarkerType)

    private enum class MarkerType {
        CARDINAL,
        DIAGONAL,
        TICK
    }

    companion object {
        private const val WIDTH = 200
        private const val HEIGHT = 25
        private const val MARKER_SPACING = 30f
        private const val FULL_WIDTH = 24 * MARKER_SPACING

        private val MARKERS = listOf(
            Marker("N", MarkerType.CARDINAL),
            Marker("195", MarkerType.TICK),
            Marker("210", MarkerType.TICK),
            Marker("NE", MarkerType.DIAGONAL),
            Marker("240", MarkerType.TICK),
            Marker("255", MarkerType.TICK),
            Marker("E", MarkerType.CARDINAL),
            Marker("285", MarkerType.TICK),
            Marker("300", MarkerType.TICK),
            Marker("SE", MarkerType.DIAGONAL),
            Marker("330", MarkerType.TICK),
            Marker("345", MarkerType.TICK),
            Marker("S", MarkerType.CARDINAL),
            Marker("15", MarkerType.TICK),
            Marker("30", MarkerType.TICK),
            Marker("SW", MarkerType.DIAGONAL),
            Marker("60", MarkerType.TICK),
            Marker("75", MarkerType.TICK),
            Marker("W", MarkerType.CARDINAL),
            Marker("105", MarkerType.TICK),
            Marker("120", MarkerType.TICK),
            Marker("NW", MarkerType.DIAGONAL),
            Marker("150", MarkerType.TICK),
            Marker("165", MarkerType.TICK)
        )
    }
}
