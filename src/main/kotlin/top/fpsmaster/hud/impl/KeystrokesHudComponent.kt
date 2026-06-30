package top.fpsmaster.hud.impl

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.network.chat.Component
import top.fpsmaster.hud.CpsTracker
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.Keystrokes

class KeystrokesHudComponent : HudComponent(
    id = "keystrokes",
    x = 10f,
    y = 68f
) {
    override fun shouldRender(): Boolean = visible && Keystrokes.isActive()

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val spacing = Keystrokes.spacing().toFloat()
        return HudSize(
            width = 60f + spacing * 2f,
            height = (if (Keystrokes.showSpace.getValue()) 72f else 60f) + spacing * 2f
        )
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val spacing = Keystrokes.spacing()
        val origin = spacing

        drawKey(guiGraphics, origin + 20, 0, 18, 18, "W", preview || mc.options.keyUp.isDown, KeyKind.W)
        drawKey(guiGraphics, 0, origin + 20, 18, 18, "A", preview || mc.options.keyLeft.isDown, KeyKind.A)
        drawKey(guiGraphics, origin + 20, origin + 20, 18, 18, "S", preview || mc.options.keyDown.isDown, KeyKind.S)
        drawKey(guiGraphics, origin + 40 + spacing, origin + 20, 18, 18, "D", preview || mc.options.keyRight.isDown, KeyKind.D)
        drawKey(guiGraphics, 0, origin + 40 + spacing, 28, 18, "LMB", preview || mc.options.keyAttack.isDown, KeyKind.MouseLeft, preview)
        drawKey(guiGraphics, origin + 30 + spacing, origin + 40 + spacing, 28, 18, "RMB", preview || mc.options.keyUse.isDown, KeyKind.MouseRight, preview)

        if (Keystrokes.showSpace.getValue()) {
            drawKey(guiGraphics, 0, 60 + spacing * 2, 58 + spacing * 2, 12, "SPACE", preview || mc.options.keyJump.isDown, KeyKind.Space)
        }
    }

    private fun drawKey(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        text: String,
        pressed: Boolean,
        kind: KeyKind,
        preview: Boolean = false
    ) {
        val left = x
        val top = y
        val right = x + width
        val bottom = y + height
        val background = if (pressed) Keystrokes.pressedColor() else Keystrokes.backgroundColor()
        val border = Keystrokes.borderColor()
        val borderWidth = Keystrokes.borderWidth.getValue().toInt()

        if (Keystrokes.shouldDrawBackground()) {
            if (borderWidth > 0 && (border ushr 24) > 0) {
                guiGraphics.fill(left - borderWidth, top - borderWidth, right + borderWidth, top, border)
                guiGraphics.fill(left - borderWidth, bottom, right + borderWidth, bottom + borderWidth, border)
                guiGraphics.fill(left - borderWidth, top, left, bottom, border)
                guiGraphics.fill(right, top, right + borderWidth, bottom, border)
            }
            if ((background ushr 24) > 0) {
                guiGraphics.fill(left, top, right, bottom, background)
            }
        }

        drawLabel(guiGraphics, left, top, width, height, text, pressed, kind, preview)
    }

    private fun drawLabel(
        guiGraphics: GuiGraphics,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        text: String,
        pressed: Boolean,
        kind: KeyKind,
        preview: Boolean
    ) {
        val color = Keystrokes.fontColor(pressed)
        if (kind == KeyKind.Space && Keystrokes.spaceStyleIndex() == 1) {
            val barWidth = (width * 0.6f).toInt()
            val barHeight = (height * 0.2f).toInt().coerceAtLeast(2)
            val barLeft = left + (width - barWidth) / 2
            val barTop = top + (height - barHeight) / 2
            guiGraphics.fill(barLeft, barTop, barLeft + barWidth, barTop + barHeight, color)
            return
        }

        if (kind.isWasd() && Keystrokes.wasdStyleIndex() == 1) {
            drawCenteredText(guiGraphics, kind.triangleText(), left, top, width, height, color)
            return
        }

        if (kind.isMouse() && Keystrokes.cpsModeIndex() != 2) {
            val cps = when {
                preview -> if (kind == KeyKind.MouseLeft) 7 else 5
                kind == KeyKind.MouseLeft -> CpsTracker.leftCps()
                else -> CpsTracker.rightCps()
            }
            if (Keystrokes.cpsModeIndex() == 0) {
                drawCenteredText(guiGraphics, text, left, top + 2, width, mc.font.lineHeight, color)
                drawCenteredText(guiGraphics, "$cps CPS", left, top + height - mc.font.lineHeight, width, mc.font.lineHeight, color)
                return
            }
            if (cps > 0) {
                drawCenteredText(guiGraphics, "$cps CPS", left, top, width, height, color)
                return
            }
        }

        drawCenteredText(guiGraphics, text, left, top, width, height, color)
    }

    private fun drawCenteredText(guiGraphics: GuiGraphics, text: String, left: Int, top: Int, width: Int, height: Int, color: Int) {
        val label = Component.literal(text)
        val textWidth = mc.font.width(label)
        val textX = left + (width - textWidth) / 2
        val textY = top + (height - mc.font.lineHeight) / 2
        guiGraphics.drawString(mc.font, label, textX, textY, color, Keystrokes.style.fontShadow.getValue())
    }

    private enum class KeyKind {
        W,
        A,
        S,
        D,
        MouseLeft,
        MouseRight,
        Space;

        fun isWasd(): Boolean = this == W || this == A || this == S || this == D

        fun isMouse(): Boolean = this == MouseLeft || this == MouseRight

        fun triangleText(): String {
            return when (this) {
                W -> "^"
                A -> "<"
                S -> "v"
                D -> ">"
                else -> ""
            }
        }
    }
}
