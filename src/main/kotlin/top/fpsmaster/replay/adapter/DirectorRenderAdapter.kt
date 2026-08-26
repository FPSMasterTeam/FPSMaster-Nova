package top.fpsmaster.replay.adapter

import top.fpsmaster.mc
import top.fpsmaster.replay.director.CameraPose

//? if >=1.21.5 {
import net.minecraft.client.Screenshot
//?} else {
/*import net.minecraft.client.Screenshot
*///?}

/**
 * Puts the director's camera where the timeline says it is, and reads finished frames back off the
 * framebuffer for the encoder.
 *
 * The camera is moved by moving the entity the camera follows, not by patching the camera itself.
 * During playback that entity is the replay's own local player, so a keyframed shot is a matter of
 * placing it — no render mixin, and free look still works between keyframes because nothing is
 * overridden when [clear] has been called.
 *
 * [CameraPose.roll] is the exception: nothing in vanilla rolls the view, so it is published in
 * [rollDegrees] for a render mixin to consume and is otherwise inert.
 */
object DirectorRenderAdapter {

    /** Roll for the current frame, in degrees. Zero when the director is not driving the camera. */
    @JvmStatic
    @Volatile
    var rollDegrees: Float = 0f
        private set

    /** FOV the director wants, or 0 to leave the player's own setting alone. */
    @JvmStatic
    @Volatile
    var fovOverride: Float = 0f
        private set

    @JvmStatic
    @Volatile
    var isDriving: Boolean = false
        private set

    /**
     * Places the camera for this frame.
     *
     * `setPos` moves both the current and previous position, so the frame does not interpolate from
     * wherever the camera was — a keyframed cut would otherwise smear across the shot boundary.
     */
    fun apply(pose: CameraPose) {
        val player = mc.player ?: return
        isDriving = true
        rollDegrees = pose.roll
        fovOverride = pose.fov
        player.setPos(pose.x, pose.y, pose.z)
        player.xo = pose.x
        player.yo = pose.y
        player.zo = pose.z
        player.xOld = pose.x
        player.yOld = pose.y
        player.zOld = pose.z
        player.yRot = pose.yaw
        player.xRot = pose.pitch
        player.yRotO = pose.yaw
        player.xRotO = pose.pitch
        player.setDeltaMovement(0.0, 0.0, 0.0)
    }

    /** Hands the camera back to the player. */
    fun clear() {
        isDriving = false
        rollDegrees = 0f
        fovOverride = 0f
    }

    /** The pose the camera is at right now, for dropping a keyframe where the operator is looking. */
    fun currentPose(): CameraPose? {
        val player = mc.player ?: return null
        return CameraPose(
            x = player.x,
            y = player.y,
            z = player.z,
            yaw = player.yRot,
            pitch = player.xRot,
            fov = mc.options.fov().get().toFloat(),
            roll = rollDegrees,
        )
    }

    fun framebufferWidth(): Int = mainTarget().width

    fun framebufferHeight(): Int = mainTarget().height

    private fun mainTarget(): com.mojang.blaze3d.pipeline.RenderTarget =
        //? if >=26 {
        /*mc.gameRenderer.mainRenderTarget()
        *///?} else {
        mc.mainRenderTarget
        //?}

    /**
     * Copies the last rendered frame as RGBA rows, top row first.
     *
     * Goes through vanilla's own screenshot path rather than reading the framebuffer directly: it
     * already handles the flip and, from 1.21.5, the move from `glReadPixels` to a command-encoder
     * copy that has no portable equivalent here. That path became asynchronous in the same release,
     * so the newer branch waits for the image it was handed.
     *
     * Returns null when the frame could not be read, which the exporter treats as a dropped frame
     * rather than a failed export.
     */
    fun captureFrame(): ByteArray? {
        val target = mainTarget()
        //? if >=1.21.5 {
        var pixels: IntArray? = null
        var width = 0
        var height = 0
        Screenshot.takeScreenshot(target) { image ->
            width = image.width
            height = image.height
            pixels = image.pixels
            image.close()
        }
        val captured = pixels ?: return null
        return toRgbaBytes(captured, width, height)
        //?} else if >=1.20 {
        /*val image = Screenshot.takeScreenshot(target) ?: return null
        return try {
            toRgbaBytes(image.pixelsRGBA, image.width, image.height)
        } finally {
            image.close()
        }
        *///?} else {
        /*val image = Screenshot.takeScreenshot(target) ?: return null
        return try {
            toRgbaBytes(image.makePixelArray(), image.width, image.height)
        } finally {
            image.close()
        }
        *///?}
    }

    /**
     * `NativeImage` packs a pixel as little-endian `0xAABBGGRR`, so writing each int low byte first
     * lays the bytes down as R, G, B, A — exactly the `rgba` FFmpeg is told to expect.
     */
    private fun toRgbaBytes(pixels: IntArray, width: Int, height: Int): ByteArray {
        val bytes = ByteArray(width * height * 4)
        var offset = 0
        for (pixel in pixels) {
            bytes[offset] = (pixel and 0xFF).toByte()
            bytes[offset + 1] = (pixel ushr 8 and 0xFF).toByte()
            bytes[offset + 2] = (pixel ushr 16 and 0xFF).toByte()
            bytes[offset + 3] = (pixel ushr 24 and 0xFF).toByte()
            offset += 4
        }
        return bytes
    }
}
