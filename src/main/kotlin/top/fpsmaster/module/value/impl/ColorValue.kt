package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.awt.Color
import java.util.function.Supplier
import kotlin.math.floor
import kotlin.math.sin

/**
 * One colour setting, mapped to Prism `COLOR`.
 *
 * HSB is stored (not packed RGB) so the picker keeps its hue while brightness or saturation is dragged
 * to zero. [Mode] carries the same animations Edge's `ColorSetting` offers, so a colour imported from an
 * Edge profile keeps looking the way the player configured it; [argb] resolves the animation and is what
 * render code should call every frame.
 */
class ColorValue(
    identity: String,
    hue: Float,
    saturation: Float,
    brightness: Float,
    alpha: Float = 1f,
    val modes: List<Mode> = Mode.ALL,
    displayable: Supplier<Boolean> = Supplier { true }
) : Value<ColorValue.Snapshot>(identity, Snapshot(hue, saturation, brightness, alpha), displayable) {

    enum class Mode(val id: String) {
        STATIC("static"),
        WAVE("wave"),
        BRIGHTNESS_WAVE("brightness-wave"),
        RAINBOW("rainbow"),
        HUE_CYCLE("hue-cycle");

        companion object {
            val ALL: List<Mode> = entries.toList()

            /** Accepts our ids/names plus the Edge `ColorType` names stored in Edge profiles. */
            fun of(id: String?): Mode? {
                if (id == null) {
                    return null
                }
                entries.firstOrNull { it.id.equals(id, true) || it.name.equals(id, true) }?.let { return it }
                return when (id.uppercase()) {
                    "WAVE_BRIGHTNESS", "BRIGHTNESSWAVE" -> BRIGHTNESS_WAVE
                    "CHROMA", "HUECYCLE" -> HUE_CYCLE
                    else -> null
                }
            }
        }
    }

    data class Snapshot(
        val hue: Float,
        val saturation: Float,
        val brightness: Float,
        val alpha: Float,
        val mode: Mode = Mode.STATIC,
        val speed: Float = 1f
    )

    override fun setValue(value: Snapshot) {
        super.setValue(
            Snapshot(
                hue = wrap(value.hue),
                saturation = value.saturation.coerceIn(0f, 1f),
                brightness = value.brightness.coerceIn(0f, 1f),
                alpha = value.alpha.coerceIn(0f, 1f),
                mode = if (modes.contains(value.mode)) value.mode else modes.first(),
                speed = value.speed.coerceIn(0.1f, 10f)
            )
        )
    }

    fun set(
        hue: Float,
        saturation: Float,
        brightness: Float,
        alpha: Float,
        mode: Mode = getValue().mode,
        speed: Float = getValue().speed
    ) {
        setValue(Snapshot(hue, saturation, brightness, alpha, mode, speed))
    }

    fun setMode(mode: Mode) {
        setValue(getValue().copy(mode = mode))
    }

    fun setSpeed(speed: Float) {
        setValue(getValue().copy(speed = speed))
    }

    /** Colour with the animation applied. [offset] shifts wave/hue animations per rendered element. */
    @JvmOverloads
    fun argb(offset: Float = 0f): Int {
        val value = getValue()
        val now = System.nanoTime()
        val shift = offset - floor(offset)
        val dynamicHue = ((now / 1_000_000_000.0 / 6.0 * value.speed) % 1.0).toFloat()
        return when (value.mode) {
            Mode.STATIC -> pack(value.hue, value.saturation, value.brightness, value.alpha)
            Mode.WAVE -> pack(value.hue, value.saturation, value.brightness, value.alpha * wave(now, value.speed, shift))
            Mode.BRIGHTNESS_WAVE ->
                pack(value.hue, value.saturation, value.brightness * wave(now, value.speed, shift), value.alpha)
            Mode.HUE_CYCLE -> pack(wrap(value.hue + dynamicHue + shift), value.saturation, value.brightness, value.alpha)
            Mode.RAINBOW -> pack(wrap(dynamicHue + shift), value.saturation, value.brightness, value.alpha)
        }
    }

    /** Colour ignoring the animation; use for previews and swatches. */
    fun staticArgb(): Int {
        val value = getValue()
        return pack(value.hue, value.saturation, value.brightness, value.alpha)
    }

    @JvmOverloads
    fun redF(offset: Float = 0f): Float = ((argb(offset) shr 16) and 255) / 255f

    @JvmOverloads
    fun greenF(offset: Float = 0f): Float = ((argb(offset) shr 8) and 255) / 255f

    @JvmOverloads
    fun blueF(offset: Float = 0f): Float = (argb(offset) and 255) / 255f

    @JvmOverloads
    fun alphaF(offset: Float = 0f): Float = ((argb(offset) ushr 24) and 255) / 255f

    companion object {
        /** Convenience for call sites that still think in 0-255 channels. */
        fun ofRgba(
            identity: String,
            red: Double,
            green: Double,
            blue: Double,
            alpha: Double,
            modes: List<Mode> = Mode.ALL,
            displayable: Supplier<Boolean> = Supplier { true }
        ): ColorValue {
            val hsb = Color.RGBtoHSB(
                red.toInt().coerceIn(0, 255),
                green.toInt().coerceIn(0, 255),
                blue.toInt().coerceIn(0, 255),
                null
            )
            return ColorValue(identity, hsb[0], hsb[1], hsb[2], (alpha / 255.0).toFloat(), modes, displayable)
        }

        private fun wave(now: Long, speed: Float, shift: Float): Float {
            val phase = sin(now / 450_000_000.0 * speed + shift * Math.PI * 2.0)
            return 0.35f + 0.65f * (((phase + 1.0) * 0.5).toFloat())
        }

        private fun wrap(value: Float): Float {
            val fraction = value - floor(value)
            return if (fraction.isNaN()) 0f else fraction
        }

        private fun pack(hue: Float, saturation: Float, brightness: Float, alpha: Float): Int {
            val rgb = Color.HSBtoRGB(hue, saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))
            return ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (rgb and 0xFFFFFF)
        }
    }
}
