package top.fpsmaster.module.impl.render

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.player.Player
import top.fpsmaster.hideGuiCompat
import top.fpsmaster.mc
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.impl.ui.HudTextColor
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

class Crosshair : Module("crosshair", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                dynamic,
                outline,
                outlineWidth,
                dot,
                gap,
                width,
                length
            )
        )
        color.addTo(this)
        outlineColor.addTo(this)
        enemyColor.addTo(this)
        friendColor.addTo(this)
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        spread = 0.0
    }

    companion object {
        private var active = false
        private var spread = 0.0

        val dynamic = NumberValue("dynamic", 3.0, 0.0, 10.0, 0.1)
        val outline = OptionValue("outline", true)
        val outlineWidth = NumberValue("outline-width", 0.8, 0.0, 10.0, 0.1) { outline.getValue() }
        val dot = OptionValue("dot", true)
        val gap = NumberValue("gap", 3.5, 0.0, 10.0, 0.1)
        val width = NumberValue("width", 0.6, 0.0, 10.0, 0.1)
        val length = NumberValue("length", 3.0, 0.0, 10.0, 0.1)
        val color = HudTextColor("color")
        val outlineColor = HudTextColor("outline", 0.0, 0.0, 0.0, 255.0)
        val enemyColor = HudTextColor("enemy", 255.0, 55.0, 50.0, 255.0)
        val friendColor = HudTextColor("friend", 20.0, 255.0, 55.0, 255.0)

        @JvmStatic
        fun isActive(): Boolean = active

        @JvmStatic
        fun render(guiGraphics: GuiGraphics) {
            if (!active || hideGuiCompat || mc.player == null) {
                return
            }

            val targetSpread = if (mc.player?.isSprinting == true) dynamic.getValue() else 0.0
            spread += (targetSpread - spread) * 0.2

            val centerX = guiGraphics.guiWidth() / 2
            val centerY = guiGraphics.guiHeight() / 2
            val gapValue = (gap.getValue() + spread).toInt()
            val lineWidth = width.getValue().toInt().coerceAtLeast(1)
            val lineLength = length.getValue().toInt().coerceAtLeast(1)
            val color = resolveColor(mc.crosshairPickEntity)

            drawLine(guiGraphics, centerX - lineWidth / 2, centerY - gapValue - lineLength, lineWidth, lineLength, color)
            drawLine(guiGraphics, centerX - lineWidth / 2, centerY + gapValue, lineWidth, lineLength, color)
            drawLine(guiGraphics, centerX - gapValue - lineLength, centerY - lineWidth / 2, lineLength, lineWidth, color)
            drawLine(guiGraphics, centerX + gapValue, centerY - lineWidth / 2, lineLength, lineWidth, color)

            if (dot.getValue()) {
                drawLine(guiGraphics, centerX - 1, centerY - 1, 2, 2, color)
            }
        }

        private fun drawLine(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, color: Int) {
            if (outline.getValue()) {
                val outlineSize = outlineWidth.getValue().toInt().coerceAtLeast(1)
                guiGraphics.fill(
                    x - outlineSize,
                    y - outlineSize,
                    x + width + outlineSize,
                    y + height + outlineSize,
                    outlineColor.argb()
                )
            }
            guiGraphics.fill(x, y, x + width, y + height, color)
        }

        private fun resolveColor(entity: Entity?): Int {
            if (entity == null) {
                return color.argb()
            }

            val player = mc.player
            if (entity is Player && player != null && entity.team != null && entity.team == player.team) {
                return friendColor.argb()
            }

            if (entity is Animal) {
                return friendColor.argb()
            }

            if (entity.type.category == MobCategory.MONSTER) {
                return enemyColor.argb()
            }

            return color.argb()
        }
    }
}
