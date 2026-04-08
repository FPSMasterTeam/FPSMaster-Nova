package top.fpsmaster.hud

import io.github.vlouboos.standaloneevent.api.EventHandler
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.mc
import java.util.ArrayDeque

object CpsTracker {
    private const val WINDOW_MS = 1_000L
    private val leftClicks = ArrayDeque<Long>()
    private val rightClicks = ArrayDeque<Long>()
    private var previousLeftDown = false
    private var previousRightDown = false

    @EventHandler
    fun onTick(@Suppress("unused") event: TickEvent) {
        mc.player ?: run {
            leftClicks.clear()
            rightClicks.clear()
            previousLeftDown = false
            previousRightDown = false
            return
        }

        val attackDown = mc.screen == null && mc.options.keyAttack.isDown
        val useDown = mc.screen == null && mc.options.keyUse.isDown
        val now = System.currentTimeMillis()

        if (attackDown && !previousLeftDown) {
            leftClicks.addLast(now)
        }
        if (useDown && !previousRightDown) {
            rightClicks.addLast(now)
        }

        previousLeftDown = attackDown
        previousRightDown = useDown
        trim(now)
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
        while (leftClicks.isNotEmpty() && now - leftClicks.first() > WINDOW_MS) {
            leftClicks.removeFirst()
        }
        while (rightClicks.isNotEmpty() && now - rightClicks.first() > WINDOW_MS) {
            rightClicks.removeFirst()
        }
    }
}
