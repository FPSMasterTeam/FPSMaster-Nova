package top.fpsmaster.hud

import net.minecraft.client.gui.GuiGraphics

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

    open fun shouldRender(): Boolean = visible

    open fun shouldRenderInEditor(): Boolean = visible

    abstract fun measure(preview: Boolean): HudSize

    protected abstract fun renderContent(guiGraphics: GuiGraphics, preview: Boolean)

    fun render(guiGraphics: GuiGraphics, preview: Boolean) {
        if ((preview && !shouldRenderInEditor()) || (!preview && !shouldRender())) {
            return
        }

        val pose = guiGraphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        renderContent(guiGraphics, preview)
        pose.popMatrix()
    }

    fun width(preview: Boolean): Float = measure(preview).width * scale

    fun height(preview: Boolean): Float = measure(preview).height * scale

    fun contains(mouseX: Float, mouseY: Float, preview: Boolean): Boolean {
        val width = width(preview)
        val height = height(preview)
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }

    fun resizeHandleContains(mouseX: Float, mouseY: Float, preview: Boolean): Boolean {
        val handleSize = RESIZE_HANDLE_SIZE
        val right = x + width(preview)
        val bottom = y + height(preview)
        return mouseX >= right - handleSize &&
            mouseX <= right &&
            mouseY >= bottom - handleSize &&
            mouseY <= bottom
    }

    fun moveTo(mouseX: Float, mouseY: Float, dragOffsetX: Float, dragOffsetY: Float, maxWidth: Float, maxHeight: Float, preview: Boolean) {
        val width = width(preview)
        val height = height(preview)
        x = (mouseX - dragOffsetX).coerceIn(0f, (maxWidth - width).coerceAtLeast(0f))
        y = (mouseY - dragOffsetY).coerceIn(0f, (maxHeight - height).coerceAtLeast(0f))
    }

    fun resizeTo(mouseX: Float, mouseY: Float, maxWidth: Float, maxHeight: Float, preview: Boolean) {
        val baseSize = measure(preview)
        val safeWidth = baseSize.width.coerceAtLeast(1f)
        val safeHeight = baseSize.height.coerceAtLeast(1f)
        val targetScaleX = (mouseX - x) / safeWidth
        val targetScaleY = (mouseY - y) / safeHeight
        scale = maxOf(targetScaleX, targetScaleY).coerceIn(MIN_SCALE, MAX_SCALE)

        val actualWidth = width(preview)
        val actualHeight = height(preview)
        x = x.coerceIn(0f, (maxWidth - actualWidth).coerceAtLeast(0f))
        y = y.coerceIn(0f, (maxHeight - actualHeight).coerceAtLeast(0f))
    }

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 4f
        const val RESIZE_HANDLE_SIZE = 8f
    }
}
