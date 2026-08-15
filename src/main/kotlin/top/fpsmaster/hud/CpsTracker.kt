package top.fpsmaster.hud

import io.github.vlouboos.standaloneevent.api.EventHandler
import top.fpsmaster.event.client.KeyEvent
import top.fpsmaster.event.client.MouseEvent
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.mc
import top.fpsmaster.screenCompat
import java.util.ArrayDeque

object CpsTracker {
    private const val WINDOW_MS = 1_000L
    private val leftClicks = ArrayDeque<Long>()
    private val rightClicks = ArrayDeque<Long>()

    @EventHandler
    fun onKeyPress(event: MouseEvent) {
        if (event.button == 0) {
            leftClicks.add(System.currentTimeMillis())
        } else if (event.button == 1) {
            rightClicks.add(System.currentTimeMillis())
        }
    }

    fun leftCps(): Int {
        trim(System.currentTimeMillis())
        return leftClicks.size
    }

    fun rightCps(): Int {
        trim(System.currentTimeMillis())
        return rightClicks.size
    }

    private fun trim(now: Long) {
        while (leftClicks.isNotEmpty() && now - leftClicks.first() >= WINDOW_MS) {
            leftClicks.removeFirst()
        }
        while (rightClicks.isNotEmpty() && now - rightClicks.first() >= WINDOW_MS) {
            rightClicks.removeFirst()
        }
    }
}
