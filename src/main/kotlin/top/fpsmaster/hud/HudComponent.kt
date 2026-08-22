package top.fpsmaster.hud

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}

abstract class HudComponent(
    val id: String,
    x: Float,
    y: Float,
    scale: Float = 1f
) {
    var x: Float = x
    var y: Float = y
    var scale: Float = scale
        set(value) {
            field = value.coerceIn(MIN_SCALE, MAX_SCALE)
        }

    var visible: Boolean = true
    var relativeX: Float = Float.NaN
    var relativeY: Float = Float.NaN

    open fun shouldRender(): Boolean = visible

    open fun shouldRenderInEditor(): Boolean = visible

    abstract fun measure(preview: Boolean): HudSize

    protected abstract fun renderContent(guiGraphics: GuiGraphics, preview: Boolean)

    open fun render(guiGraphics: GuiGraphics, preview: Boolean) {
        if ((preview && !shouldRenderInEditor()) || (!preview && !shouldRender())) {
            return
        }

        renderAt(guiGraphics, x, y, scale, preview)
    }

    fun renderAt(guiGraphics: GuiGraphics, renderX: Float, renderY: Float, renderScale: Float, preview: Boolean) {
        val pose = guiGraphics.pose()
        //? if >=1.21.5 {
        pose.pushMatrix()
        pose.translate(renderX, renderY)
        pose.scale(renderScale, renderScale)
        renderContent(guiGraphics, preview)
        pose.popMatrix()
        //?} else {
        /*pose.pushPose()
        pose.translate(renderX.toDouble(), renderY.toDouble(), 0.0)
        pose.scale(renderScale, renderScale, 1f)
        renderContent(guiGraphics, preview)
        pose.popPose()
        *///?}
    }

    fun width(preview: Boolean): Float = measure(preview).width * scale

    fun height(preview: Boolean): Float = measure(preview).height * scale

    fun adaptToSurface(surfaceWidth: Float, surfaceHeight: Float, preview: Boolean) {
        val availableX = (surfaceWidth - width(preview)).coerceAtLeast(0f)
        val availableY = (surfaceHeight - height(preview)).coerceAtLeast(0f)
        if (relativeX.isNaN()) relativeX = if (availableX == 0f) 0f else (x / availableX).coerceIn(0f, 1f)
        if (relativeY.isNaN()) relativeY = if (availableY == 0f) 0f else (y / availableY).coerceIn(0f, 1f)
        x = relativeX * availableX
        y = relativeY * availableY
    }

    fun place(x: Float, y: Float, scale: Float, surfaceWidth: Float, surfaceHeight: Float, preview: Boolean) {
        this.scale = scale
        val availableX = (surfaceWidth - width(preview)).coerceAtLeast(0f)
        val availableY = (surfaceHeight - height(preview)).coerceAtLeast(0f)
        relativeX = if (availableX == 0f) 0f else (x / availableX).coerceIn(0f, 1f)
        relativeY = if (availableY == 0f) 0f else (y / availableY).coerceIn(0f, 1f)
        this.x = relativeX * availableX
        this.y = relativeY * availableY
    }

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 4f
    }
}
