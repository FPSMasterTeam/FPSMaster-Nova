package top.fpsmaster.hud.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.Heightmap
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.MiniMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

class MiniMapHudComponent : HudComponent(
    id = "mini_map",
    x = 10f,
    y = 10f
) {
    override fun shouldRender(): Boolean = visible && MiniMap.isActive() && mc.player != null && mc.level != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize = HudSize(width = SIZE.toFloat(), height = SIZE.toFloat())

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        guiGraphics.fill(0, 0, SIZE, SIZE, 0x66000000)
        drawCircle(guiGraphics, CENTER, CENTER, MAP_RADIUS + 4, 0xAA000000.toInt())
        drawCircle(guiGraphics, CENTER, CENTER, MAP_RADIUS + 2, 0xFF1E242C.toInt())
        drawCircle(guiGraphics, CENTER, CENTER, MAP_RADIUS, 0xFF101418.toInt())

        if (preview) {
            renderPreview(guiGraphics)
            return
        }

        val player = mc.player ?: return
        val level = mc.level ?: return
        val cells = SAMPLE_CELLS
        val cellSize = MAP_DIAMETER.toDouble() / cells
        val radius = MiniMap.radius.getValue().toInt().coerceAtLeast(1)
        val centerX = player.blockX
        val centerZ = player.blockZ
        val yaw = Math.toRadians(player.yRot.toDouble())
        val sinYaw = sin(yaw)
        val cosYaw = cos(yaw)
        val playerY = player.blockY

        for (gridZ in 0 until cells) {
            for (gridX in 0 until cells) {
                val localX = (gridX + 0.5) / cells * 2.0 - 1.0
                val localZ = (gridZ + 0.5) / cells * 2.0 - 1.0
                if (localX * localX + localZ * localZ > 1.0) {
                    continue
                }

                val screenX = localX * radius
                val screenZ = localZ * radius
                val worldX = centerX + (screenX * cosYaw + screenZ * sinYaw).roundToInt()
                val worldZ = centerZ + (screenX * sinYaw - screenZ * cosYaw).roundToInt()
                val y = sampledY(worldX, worldZ, playerY)
                val pos = BlockPos(worldX, y, worldZ)
                val state = level.getBlockState(pos)
                val color = if (state.isAir) 0xFF202020.toInt() else state.getMapColor(level, pos).col or 0xFF000000.toInt()
                val shaded = shade(color, y - playerY)
                val x1 = MAP_LEFT + floor(gridX * cellSize).toInt()
                val z1 = MAP_TOP + floor(gridZ * cellSize).toInt()
                val x2 = MAP_LEFT + floor((gridX + 1) * cellSize).toInt() + 1
                val z2 = MAP_TOP + floor((gridZ + 1) * cellSize).toInt() + 1
                guiGraphics.fill(x1, z1, x2, z2, shaded)
            }
        }

        if (MiniMap.showPlayers.getValue()) {
            level.players().forEach { other ->
                if (other == player || other.isInvisible) {
                    return@forEach
                }

                val dx = other.x - player.x
                val dz = other.z - player.z
                val screenX = dx * cosYaw + dz * sinYaw
                val screenZ = dx * sinYaw - dz * cosYaw
                if (abs(screenX) > radius || abs(screenZ) > radius) {
                    return@forEach
                }
                if (screenX * screenX + screenZ * screenZ > radius * radius) {
                    return@forEach
                }

                val px = (CENTER + screenX / radius * (MAP_RADIUS - 4.0)).roundToInt()
                val pz = (CENTER + screenZ / radius * (MAP_RADIUS - 4.0)).roundToInt()
                guiGraphics.fill(px - 3, pz - 3, px + 4, pz + 4, 0xAA000000.toInt())
                guiGraphics.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFFFF4545.toInt())
            }
        }

        drawPlayerArrow(guiGraphics)
        drawCircleOutline(guiGraphics, CENTER, CENTER, MAP_RADIUS + 1, 0xCCFFFFFF.toInt())
    }

    private fun renderPreview(guiGraphics: GuiGraphics) {
        val cellSize = MAP_DIAMETER.toDouble() / SAMPLE_CELLS
        for (z in 0 until SAMPLE_CELLS) {
            for (x in 0 until SAMPLE_CELLS) {
                val localX = (x + 0.5) / SAMPLE_CELLS * 2.0 - 1.0
                val localZ = (z + 0.5) / SAMPLE_CELLS * 2.0 - 1.0
                if (localX * localX + localZ * localZ > 1.0) {
                    continue
                }
                val color = if ((x + z) % 2 == 0) 0xFF315E3C.toInt() else 0xFF406F47.toInt()
                val x1 = MAP_LEFT + floor(x * cellSize).toInt()
                val z1 = MAP_TOP + floor(z * cellSize).toInt()
                val x2 = MAP_LEFT + floor((x + 1) * cellSize).toInt() + 1
                val z2 = MAP_TOP + floor((z + 1) * cellSize).toInt() + 1
                guiGraphics.fill(x1, z1, x2, z2, color)
            }
        }
        drawPlayerArrow(guiGraphics)
        drawCircleOutline(guiGraphics, CENTER, CENTER, MAP_RADIUS + 1, 0xCCFFFFFF.toInt())
    }

    private fun sampledY(worldX: Int, worldZ: Int, playerY: Int): Int {
        val level = mc.level ?: return playerY
        val surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1
        if (playerY >= surfaceY - 2) {
            return surfaceY
        }

        val highY = (playerY + 3).coerceAtMost(surfaceY)
        val lowY = (playerY - 30).coerceAtLeast(level.minY)
        for (y in highY downTo lowY) {
            val pos = BlockPos(worldX, y, worldZ)
            val state = level.getBlockState(pos)
            if (!state.isAir && !state.fluidState.isEmpty) {
                return y
            }
            if (!state.isAir && state.isSolidRender) {
                return y
            }
        }
        return surfaceY
    }

    private fun shade(color: Int, heightDelta: Int): Int {
        val adjustment = heightDelta.coerceIn(-8, 8) * 4
        val r = ((color shr 16) and 0xFF).plus(adjustment).coerceIn(0, 255)
        val g = ((color shr 8) and 0xFF).plus(adjustment).coerceIn(0, 255)
        val b = (color and 0xFF).plus(adjustment).coerceIn(0, 255)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun drawPlayerArrow(guiGraphics: GuiGraphics) {
        guiGraphics.fill(CENTER - 3, CENTER - 2, CENTER + 4, CENTER + 3, 0xCC000000.toInt())
        guiGraphics.fill(CENTER - 2, CENTER - 2, CENTER + 3, CENTER + 3, 0xFFFFFFFF.toInt())
        guiGraphics.fill(CENTER, CENTER - 8, CENTER + 1, CENTER - 2, 0xFFFFFFFF.toInt())
        guiGraphics.fill(CENTER - 1, CENTER - 6, CENTER + 2, CENTER - 5, 0xFFFFFFFF.toInt())
    }

    private fun drawCircle(guiGraphics: GuiGraphics, centerX: Int, centerY: Int, radius: Int, color: Int) {
        for (dy in -radius..radius) {
            val halfWidth = kotlin.math.sqrt((radius * radius - dy * dy).toDouble()).roundToInt()
            guiGraphics.fill(centerX - halfWidth, centerY + dy, centerX + halfWidth + 1, centerY + dy + 1, color)
        }
    }

    private fun drawCircleOutline(guiGraphics: GuiGraphics, centerX: Int, centerY: Int, radius: Int, color: Int) {
        for (angle in 0 until 360 step 3) {
            val radians = Math.toRadians(angle.toDouble())
            val x = centerX + (cos(radians) * radius).roundToInt()
            val y = centerY + (sin(radians) * radius).roundToInt()
            guiGraphics.fill(x, y, x + 1, y + 1, color)
        }
    }

    companion object {
        private const val SIZE = 84
        private const val CENTER = SIZE / 2
        private const val MAP_RADIUS = 38
        private const val MAP_DIAMETER = MAP_RADIUS * 2
        private const val MAP_LEFT = CENTER - MAP_RADIUS
        private const val MAP_TOP = CENTER - MAP_RADIUS
        private const val SAMPLE_CELLS = 57
    }
}
