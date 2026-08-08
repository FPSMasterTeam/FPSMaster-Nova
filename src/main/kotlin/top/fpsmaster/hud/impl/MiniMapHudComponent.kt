package top.fpsmaster.hud.impl

import com.mojang.blaze3d.platform.NativeImage
//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.client.multiplayer.ClientLevel
//? if >=1.21.5 {
import net.minecraft.client.renderer.RenderPipelines
//?}
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.BlockPos
//? if >=1.21.11 {
import net.minecraft.resources.Identifier
//?} else {
/*import net.minecraft.resources.ResourceLocation*/
//?}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.levelgen.Heightmap
//? if >=1.20 {
import net.minecraft.world.level.material.MapColor
//?} else {
/*import net.minecraft.world.level.material.MaterialColor as MapColor*/
//?}
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.identifier
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.MiniMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

class MiniMapHudComponent : HudComponent(
    id = "mini_map",
    x = 10f,
    y = 10f
) {
    // --- Toroidal world-block colour cache (source of truth, world aligned, north up) ---
    private var cacheSize = 0
    private var halfExtent = 0
    private var colorCache = IntArray(0)
    private var keyCache = LongArray(0)
    private var ageCache = IntArray(0)
    private var cacheLevel: ClientLevel? = null
    private var refreshCursor = 0
    private var frame = 0

    // Skip the (expensive) per-frame super-sampled composite + GPU upload when nothing that affects the
    // baked texture changed: player position/rotation, radius/shape, or the terrain colour cache.
    private var cacheDirty = true
    private var lastCompositeKey = ""

    // --- GPU side: a small display texture composited from the cache each frame ---
    private var texture: DynamicTexture? = null
    private var displayImage: NativeImage? = null
    //? if >=1.21.11 {
    private var textureId: Identifier? = null
    //?} else {
    /*private var textureId: ResourceLocation? = null*/
    //?}

    override fun shouldRender(): Boolean = visible && MiniMap.isActive() && mc.player != null && mc.level != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize = HudSize(width = SIZE.toFloat(), height = SIZE.toFloat())

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val circle = MiniMap.isCircle()

        if (preview) {
            drawBackdrop(guiGraphics, circle)
            renderPreview(guiGraphics, circle)
            return
        }

        val player = mc.player ?: return
        val level = mc.level ?: return
        val radius = MiniMap.radius.getValue().toInt().coerceAtLeast(1)

        frame++
        ensureResources(radius)
        val image = displayImage
        val tex = texture
        val id = textureId
        if (image == null || tex == null || id == null) {
            return
        }

        maintainCache(level, player)
        // The whole minimap (background rings, terrain, outline) is baked into the texture with
        // super-sampled anti-aliasing, then drawn with a single blit. Re-bake only when something
        // visible changed — a stationary player with a still view reuses last frame's texture.
        val key = "${(player.x / 0.05).toLong()}:${(player.z / 0.05).toLong()}:${(player.yRot / 0.5).toLong()}:$radius:$circle"
        if (cacheDirty || key != lastCompositeKey) {
            composite(image, player, radius, circle)
            tex.upload()
            lastCompositeKey = key
            cacheDirty = false
        }
        //? if >=1.21.5 {
        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            id,
            0,
            0,
            0f,
            0f,
            SIZE,
            SIZE,
            TEX_SIZE,
            TEX_SIZE
        )
        //?} else {
        /*guiGraphics.blit(
            id,
            0,
            0,
            0f,
            0f,
            SIZE,
            SIZE,
            TEX_SIZE,
            TEX_SIZE
        )*/
        //?}

        val yaw = Math.toRadians(player.yRot.toDouble())
        val sinYaw = sin(yaw)
        val cosYaw = cos(yaw)
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
                if (circle && screenX * screenX + screenZ * screenZ > radius * radius) {
                    return@forEach
                }

                val px = (CENTER + screenX / radius * (MAP_RADIUS - 4.0)).roundToInt()
                val pz = (CENTER + screenZ / radius * (MAP_RADIUS - 4.0)).roundToInt()
                guiGraphics.fill(px - 3, pz - 3, px + 4, pz + 4, 0xAA000000.toInt())
                guiGraphics.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFFFF4545.toInt())
            }
        }

        drawPlayerArrow(guiGraphics)
    }

    // ---------------------------------------------------------------------------------
    // Resource lifecycle
    // ---------------------------------------------------------------------------------

    private fun ensureResources(radius: Int) {
        // The visible area is a circle of `radius` blocks; for the square shape a rotated
        // square reaches radius*sqrt(2) at the corners, so keep a margin above 1.42.
        val desiredHalf = ceil(radius * 1.5).toInt() + 2
        if (desiredHalf != halfExtent || colorCache.isEmpty()) {
            halfExtent = desiredHalf
            cacheSize = halfExtent * 2 + 1
            val cells = cacheSize * cacheSize
            colorCache = IntArray(cells)
            keyCache = LongArray(cells) { EMPTY_KEY }
            ageCache = IntArray(cells)
            refreshCursor = 0
            cacheLevel = null
        }

        if (texture == null) {
            val image = NativeImage(TEX_SIZE, TEX_SIZE, true)
            val id = identifier("minimap/dynamic")
            //? if >=1.21.5 {
            val dynamic = DynamicTexture({ "fpsmaster-minimap" }, image)
            //?} else {
            /*val dynamic = DynamicTexture(image)*/
            //?}
            mc.textureManager.register(id, dynamic)
            texture = dynamic
            displayImage = image
            textureId = id
        }
    }

    // ---------------------------------------------------------------------------------
    // Incremental cache maintenance
    // ---------------------------------------------------------------------------------

    private fun maintainCache(level: ClientLevel, player: Player) {
        if (cacheLevel !== level) {
            keyCache.fill(EMPTY_KEY)
            cacheLevel = level
        }

        val centerX = player.blockX
        val centerZ = player.blockZ
        val playerY = player.blockY
        val total = cacheSize * cacheSize

        // Fill pass: bring every newly visible (or scrolled in) block into the cache.
        var fillBudget = FILL_BUDGET
        var idx = 0
        while (idx < total && fillBudget > 0) {
            val wx = centerX - halfExtent + idx % cacheSize
            val wz = centerZ - halfExtent + idx / cacheSize
            val slot = slotIndex(wx, wz)
            if (keyCache[slot] != packKey(wx, wz)) {
                store(slot, wx, wz, level, playerY)
                fillBudget--
            }
            idx++
        }

        // Refresh pass: round-robin re-sample of already cached blocks so that edits,
        // lighting and height changes show up within a bounded amount of time.
        var refreshBudget = REFRESH_BUDGET
        var scanned = 0
        while (scanned < total && refreshBudget > 0) {
            val cursor = refreshCursor
            val wx = centerX - halfExtent + cursor % cacheSize
            val wz = centerZ - halfExtent + cursor / cacheSize
            val slot = slotIndex(wx, wz)
            if (keyCache[slot] == packKey(wx, wz) && frame - ageCache[slot] > MAX_AGE) {
                store(slot, wx, wz, level, playerY)
                refreshBudget--
            }
            refreshCursor = (refreshCursor + 1) % total
            scanned++
        }
    }

    private fun store(slot: Int, wx: Int, wz: Int, level: ClientLevel, playerY: Int) {
        colorCache[slot] = computeColor(level, wx, wz, playerY)
        keyCache[slot] = packKey(wx, wz)
        ageCache[slot] = frame
        cacheDirty = true
    }

    private fun computeColor(level: ClientLevel, wx: Int, wz: Int, playerY: Int): Int {
        val y = sampledY(wx, wz, playerY)
        val pos = BlockPos(wx, y, wz)
        val state = level.getBlockState(pos)
        if (state.isAir) {
            return BASE_COLOR
        }
        val mapColor = state.getMapColor(level, pos)
        if (mapColor == MapColor.NONE) {
            return BASE_COLOR
        }

        // Vanilla-style relief shading: compare this column's height to the column one
        // block to the (world) north and pick a brightness step. This is what gives the
        // map its lit/shaded depth instead of a flat fill, and being world-aligned it
        // stays stable while the view rotates.
        val northY = sampledY(wx, wz - 1, playerY)
        val brightness = when {
            y > northY -> MapColor.Brightness.HIGH
            y < northY -> MapColor.Brightness.LOW
            else -> MapColor.Brightness.NORMAL
        }
        //? if >=1.21.5 {
        return mapColor.calculateARGBColor(brightness) or 0xFF000000.toInt()
        //?} else {
        /*// calculateRGBColor returns an ABGR-packed int on 1.20.1; repack to ARGB.
        val abgr = mapColor.calculateRGBColor(brightness)
        return 0xFF000000.toInt() or ((abgr and 0xFF) shl 16) or (abgr and 0xFF00) or ((abgr ushr 16) and 0xFF)*/
        //?}
    }

    private fun sampledY(worldX: Int, worldZ: Int, playerY: Int): Int {
        val level = mc.level ?: return playerY
        val surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1
        if (playerY >= surfaceY - 2) {
            return surfaceY
        }

        val highY = (playerY + 3).coerceAtMost(surfaceY)
        //? if >=1.21.5 {
        val lowY = (playerY - 30).coerceAtLeast(level.minY)
        //?} else {
        /*val lowY = (playerY - 30).coerceAtLeast(level.minBuildHeight)*/
        //?}
        for (y in highY downTo lowY) {
            val pos = BlockPos(worldX, y, worldZ)
            val state = level.getBlockState(pos)
            if (!state.isAir && !state.fluidState.isEmpty) {
                return y
            }
            //? if >=1.21.5 {
            if (!state.isAir && state.isSolidRender) {
                return y
            }
            //?} else {
            /*if (!state.isAir && state.isSolidRender(level, pos)) {
                return y
            }*/
            //?}
        }
        return surfaceY
    }

    private fun slotIndex(wx: Int, wz: Int): Int =
        Math.floorMod(wx, cacheSize) + Math.floorMod(wz, cacheSize) * cacheSize

    private fun packKey(wx: Int, wz: Int): Long =
        (wx.toLong() shl 32) or (wz.toLong() and 0xFFFFFFFFL)

    private fun cachedColor(wx: Int, wz: Int): Int {
        val slot = slotIndex(wx, wz)
        return if (keyCache[slot] == packKey(wx, wz)) colorCache[slot] else BASE_COLOR
    }

    // ---------------------------------------------------------------------------------
    // Per-frame composite: bake the whole minimap (rings + terrain + outline) into the
    // texture with super-sampling. Terrain is sampled nearest (crisp, no zoom-in blur),
    // and the disc/square boundary plus outline ring get anti-aliased coverage from the
    // sub-samples, which removes the jagged edge and the grainy outline dots.
    // ---------------------------------------------------------------------------------

    private fun composite(image: NativeImage, player: Player, radius: Int, circle: Boolean) {
        val yaw = Math.toRadians(player.yRot.toDouble())
        val sinYaw = sin(yaw)
        val cosYaw = cos(yaw)
        val centerX = player.x
        val centerZ = player.z
        val blocksPerTexel = radius / R_MAP
        val samples = SUPER_SAMPLES * SUPER_SAMPLES

        for (py in 0 until TEX_SIZE) {
            for (px in 0 until TEX_SIZE) {
                var aSum = 0.0
                var rSum = 0.0
                var gSum = 0.0
                var bSum = 0.0

                for (sj in 0 until SUPER_SAMPLES) {
                    for (si in 0 until SUPER_SAMPLES) {
                        val sx = px + (si + 0.5) / SUPER_SAMPLES
                        val sy = py + (sj + 0.5) / SUPER_SAMPLES
                        val ddx = sx - TEX_CENTER
                        val ddy = sy - TEX_CENTER
                        val dist = if (circle) {
                            kotlin.math.hypot(ddx, ddy)
                        } else {
                            kotlin.math.max(abs(ddx), abs(ddy))
                        }

                        val argb = when {
                            dist > R_OUTER -> 0
                            dist > R_BORDER -> SHADOW_COLOR
                            dist > R_RING -> BORDER_COLOR
                            dist > R_MAP -> OUTLINE_COLOR
                            else -> {
                                val ox = ddx * blocksPerTexel
                                val oz = ddy * blocksPerTexel
                                val wx = floor(centerX + (ox * cosYaw + oz * sinYaw)).toInt()
                                val wz = floor(centerZ + (ox * sinYaw - oz * cosYaw)).toInt()
                                cachedColor(wx, wz)
                            }
                        }

                        if (argb != 0) {
                            val a = (argb ushr 24) and 0xFF
                            aSum += a
                            rSum += a * ((argb ushr 16) and 0xFF)
                            gSum += a * ((argb ushr 8) and 0xFF)
                            bSum += a * (argb and 0xFF)
                        }
                    }
                }

                val alpha = (aSum / samples).roundToInt().coerceIn(0, 255)
                if (alpha == 0) {
                    //? if >=1.21.5 {
                    image.setPixelABGR(px, py, 0)
                    //?} else {
                    /*image.setPixelRGBA(px, py, 0)*/
                    //?}
                    continue
                }
                val r = (rSum / aSum).roundToInt().coerceIn(0, 255)
                val g = (gSum / aSum).roundToInt().coerceIn(0, 255)
                val b = (bSum / aSum).roundToInt().coerceIn(0, 255)
                //? if >=1.21.5 {
                image.setPixelABGR(px, py, (alpha shl 24) or (b shl 16) or (g shl 8) or r)
                //?} else {
                /*image.setPixelRGBA(px, py, (alpha shl 24) or (b shl 16) or (g shl 8) or r)*/
                //?}
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Static decoration (backdrop / outline / arrow) and editor preview
    // ---------------------------------------------------------------------------------

    private fun renderPreview(guiGraphics: GuiGraphics, circle: Boolean) {
        val cellSize = MAP_DIAMETER.toDouble() / SAMPLE_CELLS
        for (z in 0 until SAMPLE_CELLS) {
            for (x in 0 until SAMPLE_CELLS) {
                val localX = (x + 0.5) / SAMPLE_CELLS * 2.0 - 1.0
                val localZ = (z + 0.5) / SAMPLE_CELLS * 2.0 - 1.0
                if (circle && localX * localX + localZ * localZ > 1.0) {
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
        drawOutline(guiGraphics, circle)
    }

    private fun drawPlayerArrow(guiGraphics: GuiGraphics) {
        guiGraphics.fill(CENTER - 3, CENTER - 2, CENTER + 4, CENTER + 3, 0xCC000000.toInt())
        guiGraphics.fill(CENTER - 2, CENTER - 2, CENTER + 3, CENTER + 3, 0xFFFFFFFF.toInt())
        guiGraphics.fill(CENTER, CENTER - 8, CENTER + 1, CENTER - 2, 0xFFFFFFFF.toInt())
        guiGraphics.fill(CENTER - 1, CENTER - 6, CENTER + 2, CENTER - 5, 0xFFFFFFFF.toInt())
    }

    private fun drawBackdrop(guiGraphics: GuiGraphics, circle: Boolean) {
        if (circle) {
            drawCircle(guiGraphics, CENTER, CENTER, MAP_RADIUS + 4, 0xAA000000.toInt())
            drawCircle(guiGraphics, CENTER, CENTER, MAP_RADIUS + 2, 0xFF1E242C.toInt())
            drawCircle(guiGraphics, CENTER, CENTER, MAP_RADIUS, 0xFF101418.toInt())
        } else {
            guiGraphics.fill(MAP_LEFT - 4, MAP_TOP - 4, MAP_RIGHT + 4, MAP_BOTTOM + 4, 0xAA000000.toInt())
            guiGraphics.fill(MAP_LEFT - 2, MAP_TOP - 2, MAP_RIGHT + 2, MAP_BOTTOM + 2, 0xFF1E242C.toInt())
            guiGraphics.fill(MAP_LEFT, MAP_TOP, MAP_RIGHT, MAP_BOTTOM, 0xFF101418.toInt())
        }
    }

    private fun drawOutline(guiGraphics: GuiGraphics, circle: Boolean) {
        val color = 0xCCFFFFFF.toInt()
        if (circle) {
            drawCircleOutline(guiGraphics, CENTER, CENTER, MAP_RADIUS + 1, color)
        } else {
            val left = MAP_LEFT - 1
            val top = MAP_TOP - 1
            val right = MAP_RIGHT + 1
            val bottom = MAP_BOTTOM + 1
            guiGraphics.fill(left, top, right, top + 1, color)
            guiGraphics.fill(left, bottom - 1, right, bottom, color)
            guiGraphics.fill(left, top, left + 1, bottom, color)
            guiGraphics.fill(right - 1, top, right, bottom, color)
        }
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
        private const val MAP_RIGHT = CENTER + MAP_RADIUS
        private const val MAP_BOTTOM = CENTER + MAP_RADIUS
        private const val SAMPLE_CELLS = 57

        // The texture covers the whole component so the background rings and the outline
        // can be baked in (and anti-aliased) instead of drawn with grainy fill() calls.
        private const val TEX_SIZE = SIZE
        private const val TEX_CENTER = TEX_SIZE / 2.0
        private const val SUPER_SAMPLES = 4

        // Concentric bands measured from the texture centre, in texels.
        private const val R_MAP = 37.0      // terrain content
        private const val R_RING = 38.5     // white outline ring
        private const val R_BORDER = 40.0   // inner dark border
        private const val R_OUTER = 42.0    // outer edge / drop shadow

        private const val OUTLINE_COLOR = 0xFFE6ECF2.toInt()
        private const val BORDER_COLOR = 0xFF1E242C.toInt()
        private const val SHADOW_COLOR = 0xCC05080A.toInt()

        private const val BASE_COLOR = 0xFF101418.toInt()
        private const val EMPTY_KEY = Long.MIN_VALUE

        // Per-frame work limits keep the cost bounded even on teleports / fast flight.
        private const val FILL_BUDGET = 2048
        private const val REFRESH_BUDGET = 256
        private const val MAX_AGE = 200
    }
}
