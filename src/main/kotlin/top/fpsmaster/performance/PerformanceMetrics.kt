package top.fpsmaster.performance

import io.github.vlouboos.standaloneevent.api.EventHandler
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.mc
import top.fpsmaster.web.network.NetworkManager
import top.fpsmaster.web.network.packets.PerformanceMetricsPacket

/**
 * Samples FPS, a rolling "low" (window minimum, a 1%-low proxy) and ping once per second and broadcasts
 * them to the ClickGUI performance-page gauge. Deliberately independent of the module system.
 */
object PerformanceMetrics {
    private const val WINDOW = 10
    private const val TICKS_PER_SAMPLE = 20
    private var tickCounter = 0
    private val window = ArrayDeque<Int>()

    @EventHandler
    fun onTick(@Suppress("UNUSED_PARAMETER") event: TickEvent) {
        if (++tickCounter < TICKS_PER_SAMPLE) return
        tickCounter = 0

        val fps = currentFps()
        window.addLast(fps)
        while (window.size > WINDOW) window.removeFirst()
        val low = window.minOrNull() ?: fps

        NetworkManager.broadcastPacket(PerformanceMetricsPacket().apply {
            this.fps = fps
            this.lowFps = low
            this.ping = currentPing()
        })
    }

    private fun currentFps(): Int {
        //? if >=1.20 {
        return mc.fps
        //?} else {
        /*return net.minecraft.client.Minecraft.fps*/
        //?}
    }

    private fun currentPing(): Int {
        val player = mc.player ?: return 0
        return mc.connection?.getPlayerInfo(player.uuid)?.latency ?: 0
    }
}
