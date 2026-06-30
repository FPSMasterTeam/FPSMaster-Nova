package top.fpsmaster.notification

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import top.fpsmaster.mc
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.roundToInt

object NotificationManager {
    private val notifications = CopyOnWriteArrayList<Notification>()

    fun add(title: String, description: String, durationSeconds: Float = 2f) {
        notifications.add(Notification(title, description, durationSeconds))
    }

    @JvmStatic
    fun render(guiGraphics: GuiGraphics) {
        val now = System.currentTimeMillis()
        var y = 20

        notifications.forEach { notification ->
            if (notification.isFinished(now)) {
                notifications.remove(notification)
                return@forEach
            }

            notification.render(guiGraphics, now, y)
            y += 40
        }
    }

    private class Notification(
        private val title: String,
        private val description: String,
        durationSeconds: Float
    ) {
        private val createdAt = System.currentTimeMillis()
        private val durationMs = (durationSeconds * 1000f).roundToInt().coerceAtLeast(300)

        fun render(guiGraphics: GuiGraphics, now: Long, y: Int) {
            val age = now - createdAt
            val font = mc.font
            val width = max(font.width(title), font.width(description)) + 28
            val slide = when {
                age < 180 -> age / 180f
                age > durationMs -> 1f - (age - durationMs) / 220f
                else -> 1f
            }.coerceIn(0f, 1f)
            val x = (-width + width * slide).roundToInt()
            val progress = (age.toFloat() / durationMs).coerceIn(0f, 1f)

            guiGraphics.fill(x, y, x + width, y + 30, 0xA0000000.toInt())
            guiGraphics.fill(x, y + 29, x + (width * progress).roundToInt(), y + 30, 0xB0FFFFFF.toInt())
            guiGraphics.fill(x + 6, y + 8, x + 10, y + 22, 0xFFFFFFFF.toInt())
            guiGraphics.drawString(font, title, x + 16, y + 4, 0xFFFFFFFF.toInt(), true)
            guiGraphics.drawString(font, description, x + 16, y + 16, 0xFFD0D0D0.toInt(), false)
        }

        fun isFinished(now: Long): Boolean {
            return now - createdAt > durationMs + 240
        }
    }
}
