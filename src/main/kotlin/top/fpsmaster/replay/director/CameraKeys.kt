package top.fpsmaster.replay.director

/** Spatial path from a keyframe towards the next one. Ignored on the last keyframe. */
enum class Transition {
    /** Straight line. */
    LINEAR,

    /** Catmull-Rom spline through the neighbouring keyframes. */
    SMOOTH,

    /** Hold this value until the next keyframe (hard cut). */
    CUT,
}

/** Speed profile along the path towards the next keyframe. */
enum class Easing {
    LINEAR,

    /** CSS `ease` — cubic-bezier(0.25, 0.1, 0.25, 1). */
    EASE,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
}

/**
 * Independently keyable camera properties. Position is one property with three components keyed
 * together; yaw, pitch, roll and FOV each have their own keyframe list and easing.
 */
enum class CameraChannel(val components: Int) {
    POSITION(3),
    YAW(1),
    PITCH(1),
    ROLL(1),
    FOV(1),
}

/** An immutable camera pose: position, orientation and field of view. */
data class CameraPose(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val fov: Float = 70f,
    val roll: Float = 0f,
)

/**
 * One key on a single [CameraChannel]. [a]/[b]/[c] are x/y/z for position and the scalar for every
 * other channel. [easing] shapes time towards the next key; [path] is the spatial interpolation and
 * only matters on [CameraChannel.POSITION].
 */
class PropKeyframe(
    @JvmField var timeMillis: Int = 0,
    @JvmField var a: Float = 0f,
    @JvmField var b: Float = 0f,
    @JvmField var c: Float = 0f,
) {
    @JvmField
    var easing: Easing = Easing.EASE_IN_OUT

    @JvmField
    var path: Transition = Transition.LINEAR

    fun copy(): PropKeyframe = PropKeyframe(timeMillis, a, b, c).also {
        it.easing = easing
        it.path = path
    }
}
