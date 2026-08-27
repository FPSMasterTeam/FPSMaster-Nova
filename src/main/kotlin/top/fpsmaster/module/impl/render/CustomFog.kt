package top.fpsmaster.module.impl.render

import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.ColorValue
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

/**
 * Replaces the world's fog colour and distances.
 *
 * The module decides *what* the fog should be; the render side asks it per frame while setting fog up.
 * Two questions are asked in order: [appliesTo], for whether this camera situation should be touched at
 * all, then [color] and [startDistance]/[endDistance] for the replacement.
 *
 * Water and lava are opt-in separately because their fog is a gameplay signal, not scenery — being
 * unable to see in lava is the point, and a player who tints it usually did not mean to.
 *
 * Modern fog is linear between a start and an end distance in the shader, so there is no exponential
 * mode here; the equivalent control is pulling [startDistance] toward the camera.
 */
class CustomFog : Module("custom-fog", Category.RENDER) {
    init {
        values.addAll(
            arrayOf(
                overrideColor,
                color,
                overrideDistance,
                startDistance,
                endDistance,
                affectWater,
                affectLava,
                affectSky
            )
        )
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
    }

    companion object {
        private var active = false

        @JvmField
        val overrideColor = OptionValue("override-color", true)
        @JvmField
        val color = ColorValue.ofRgba("color", 0.0, 200.0, 255.0, 255.0) { overrideColor.getValue() }

        @JvmField
        val overrideDistance = OptionValue("override-distance", true)

        @JvmField
        val startDistance = NumberValue("start-distance", 32.0, 0.0, 512.0, 1.0) { overrideDistance.getValue() }

        @JvmField
        val endDistance = NumberValue("end-distance", 96.0, 1.0, 512.0, 1.0) { overrideDistance.getValue() }

        @JvmField
        val affectWater = OptionValue("affect-water", false)

        @JvmField
        val affectLava = OptionValue("affect-lava", false)

        /** Tints the sky's fog band to match. Off leaves the horizon vanilla, which reads as haze. */
        @JvmField
        val affectSky = OptionValue("affect-sky", true)

        @JvmStatic
        fun isActive(): Boolean = active

        /** Whether the fog for the camera's current medium should be replaced. */
        @JvmStatic
        fun appliesTo(inWater: Boolean, inLava: Boolean): Boolean = when {
            !active -> false
            inWater -> affectWater.getValue()
            inLava -> affectLava.getValue()
            else -> true
        }

        @JvmStatic
        fun overridesColor(): Boolean = active && overrideColor.getValue()

        @JvmStatic
        fun overridesDistance(): Boolean = active && overrideDistance.getValue()

        @JvmStatic
        fun overridesSky(): Boolean = active && affectSky.getValue()

        @JvmStatic
        fun redFraction(): Float = ((colorArgb() ushr 16) and 255) / 255f

        @JvmStatic
        fun greenFraction(): Float = ((colorArgb() ushr 8) and 255) / 255f

        @JvmStatic
        fun blueFraction(): Float = (colorArgb() and 255) / 255f

        /** Opaque ARGB, for call sites that pass fog colour as a packed int. */
        @JvmStatic
        fun colorArgb(): Int = (0xFF shl 24) or (color.argb() and 0xFFFFFF)

        /**
         * Fog start in blocks. Kept at least one block below [endDistance] whatever the sliders say:
         * a start at or past the end is a divide-by-zero in the fog factor, and the world flickers
         * between fully fogged and not fogged at all.
         */
        @JvmStatic
        fun startDistance(original: Float): Float {
            if (!overridesDistance()) {
                return original
            }
            return minOf(startDistance.getValue().toFloat(), endDistance(original) - 1f)
        }

        @JvmStatic
        fun endDistance(original: Float): Float {
            if (!overridesDistance()) {
                return original
            }
            return maxOf(endDistance.getValue().toFloat(), 1f)
        }
    }
}
