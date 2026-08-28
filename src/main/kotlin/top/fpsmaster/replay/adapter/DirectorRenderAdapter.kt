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
 * [rollDegrees] and `MixinCamera` folds it into the camera's rotation. FOV goes the same way
 * through [modifyFov], because the projection is not built from the entity either.
 */
object DirectorRenderAdapter {

    /** Roll the timeline asks for. Only meaningful while [isDriving]. */
    @Volatile
    private var keyedRoll: Float = 0f

    /**
     * Roll the operator has dialled in by hand with the roll keys.
     *
     * Kept apart from [keyedRoll] because a track with no ROLL key must not wipe it: `sample` falls
     * back to [currentPose] for the channels nobody keyed, so a hand-set tilt rides through a
     * keyframed shot instead of snapping level again on the next tick.
     */
    @Volatile
    private var operatorRoll: Float = 0f

    /** Roll for the current frame, in degrees. Zero when nothing is tilting the camera. */
    @JvmStatic
    val rollDegrees: Float
        get() = if (isDriving) keyedRoll else operatorRoll

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
        keyedRoll = pose.roll
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

    /**
     * Hands the camera back to the player for this frame.
     *
     * The hand-set roll survives on purpose: a project with an empty camera track lands here every
     * single tick, and levelling the camera there would make the roll keys do nothing at all until
     * the operator drops a keyframe.
     */
    fun clear() {
        isDriving = false
        keyedRoll = 0f
        fovOverride = 0f
    }

    /** End of the session. Forgets the hand-set roll too, so ordinary play is not left tilted. */
    fun reset() {
        clear()
        operatorRoll = 0f
    }

    /**
     * Tilts the camera by [degrees].
     *
     * Moves both rolls so the keys work whether or not the shot is keyed. When ROLL *is* keyed the
     * next sample overwrites [keyedRoll] and the nudge is gone within the tick — keys win, which is
     * the same rule every other channel follows.
     */
    fun nudgeRoll(degrees: Float) {
        operatorRoll = wrapRoll(operatorRoll + degrees)
        keyedRoll = wrapRoll(keyedRoll + degrees)
    }

    /** Back to level. */
    fun resetRoll() {
        operatorRoll = 0f
        keyedRoll = 0f
    }

    /**
     * The FOV to render this frame with.
     *
     * Called from the three places the FOV is worked out — the method is `GameRenderer.getFov`
     * returning a double below 1.21.5, the same name returning a float up to 1.21.11, and
     * `Camera.calculateFov` from 26 on. Without this hook the FOV channel is keyed, saved,
     * interpolated and then dropped on the floor: the shot renders at whatever the operator happens
     * to have on their own FOV slider, and the export comes out the same way.
     */
    @JvmStatic
    fun modifyFov(fov: Float): Float {
        if (!isDriving) {
            return fov
        }
        val wanted = fovOverride
        // Zero is "the timeline has nothing to say". The bounds are the vanilla slider's: a
        // hand-edited project file is the only way past them, and 0 or 400 is a black screen.
        return if (wanted > 0f) wanted.coerceIn(MIN_FOV, MAX_FOV) else fov
    }

    private fun wrapRoll(degrees: Float): Float {
        var wrapped = degrees % 360f
        if (wrapped > 180f) {
            wrapped -= 360f
        }
        if (wrapped < -180f) {
            wrapped += 360f
        }
        return wrapped
    }

    private const val MIN_FOV = 30f
    private const val MAX_FOV = 110f

    /**
     * The pose the camera is at right now, for dropping a keyframe where the operator is looking.
     *
     * The FOV is the one actually on screen, not the one on the options slider: once the timeline is
     * driving, the slider still reads whatever the player set it to, and keying off it would file a
     * keyframe for a shot nobody is looking at. It is also what `sample` holds unkeyed channels at,
     * so the same rule keeps a hand-set tilt or a keyed FOV from being quietly reset each tick.
     */
    fun currentPose(): CameraPose? {
        val player = mc.player ?: return null
        return CameraPose(
            x = player.x,
            y = player.y,
            z = player.z,
            yaw = player.yRot,
            pitch = player.xRot,
            fov = activeFov(),
            roll = rollDegrees,
        )
    }

    /**
     * The FOV the shot is at right now — keyed if the timeline has one, the player's slider if not.
     *
     * Read by the director screen as well as [currentPose], so what the readout shows and what
     * "key camera" files can never disagree.
     */
    @JvmStatic
    fun activeFov(): Float {
        val keyed = fovOverride
        if (isDriving && keyed > 0f) {
            return keyed.coerceIn(MIN_FOV, MAX_FOV)
        }
        return mc.options.fov().get().toFloat()
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
            // 原始内存序（0xAABBGGRR），正好是 toRgbaBytes 期待的低字节在前。`pixels` 那个取值
            // 器给的是 ARGB，用它导出的整段视频红蓝互换。
            pixels = image.pixelsABGR
            image.close()
        }
        // 1.21.5 起这条路是异步的：截图排一个 GPU fence，上面那个回调要等 fence 亮了才跑，所以
        // 同一帧里直接读 pixels 永远是 null——导出会安安静静地写出零帧。executePendingTasks 是
        // 公开的、非阻塞的 fence 队列排水口（awaitCompletion(0)），只能在渲染线程上排，而这里
        // 就是渲染线程，所以转着等即可：fence 由 GPU 点亮，不依赖本线程往下走，不会自锁。
        val deadline = System.nanoTime() + CAPTURE_TIMEOUT_NANOS
        while (pixels == null && System.nanoTime() < deadline) {
            com.mojang.blaze3d.systems.RenderSystem.executePendingTasks()
            Thread.onSpinWait()
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
            // 1.20 之前只有 makePixelArray，它给的是 ARGB（A<<24|R<<16|G<<8|B），跟原始内存序
            // 红蓝相反，所以走另一个写法。
            toArgbRgbaBytes(image.makePixelArray(), image.width, image.height)
        } finally {
            image.close()
        }
        *///?}
    }

    /**
     * `NativeImage` holds a pixel as little-endian `0xAABBGGRR`, so writing each int low byte first
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

    /**
     * The same, for the accessors that hand back `0xAARRGGBB` — `makePixelArray` below 1.20 and
     * `getPixels` from 1.21.5. Red and blue are swapped relative to the raw layout, and getting it
     * wrong is not a crash: it is an export that looks fine in the client and comes out of FFmpeg
     * with the sky orange.
     */
    @Suppress("unused")
    private fun toArgbRgbaBytes(pixels: IntArray, width: Int, height: Int): ByteArray {
        val bytes = ByteArray(width * height * 4)
        var offset = 0
        for (pixel in pixels) {
            bytes[offset] = (pixel ushr 16 and 0xFF).toByte()
            bytes[offset + 1] = (pixel ushr 8 and 0xFF).toByte()
            bytes[offset + 2] = (pixel and 0xFF).toByte()
            bytes[offset + 3] = (pixel ushr 24 and 0xFF).toByte()
            offset += 4
        }
        return bytes
    }

    /**
     * How long [captureFrame] will spin draining GPU fences for one frame.
     *
     * Generous on purpose: overshooting costs a slow export, undershooting drops the frame.
     */
    private const val CAPTURE_TIMEOUT_NANOS = 1_000_000_000L
}
