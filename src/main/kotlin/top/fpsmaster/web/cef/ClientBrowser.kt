package top.fpsmaster.web.cef

//? if >=1.21.5 {
import com.mojang.blaze3d.pipeline.RenderPipeline
//?}
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowser
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowserSettings
import net.ccbluex.liquidbounce.mcef.cef.MCEFClient
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
//? if >=1.20 {
import net.minecraft.client.gui.navigation.ScreenRectangle
//?}
import org.cef.browser.CefBrowser
import org.lwjgl.glfw.GLFW
import org.cef.browser.CefFrame
import org.cef.handler.CefAcceleratedPaintInfo
import org.cef.handler.CefScreenInfo
import top.fpsmaster.logger
import top.fpsmaster.mc
//? if >=1.21.5 {
import top.fpsmaster.mixin.interfaces.IGuiGraphics
//?}
import top.fpsmaster.module.impl.auxiliary.ClientSettings
//? if >=1.21.5 {
import top.fpsmaster.render.shaders.getShader
import top.fpsmaster.render.shaders.init
import top.fpsmaster.render.shaders.shaders
import top.fpsmaster.web.TexQuadGuiElementRenderState
//?}
// 1.20.1 immediate-mode CEF quad rendering (unused on 1.21.5+).
import com.mojang.blaze3d.systems.RenderSystem
//? if >=1.20.5 && <1.21.5 {
/*import com.mojang.blaze3d.vertex.BufferUploader*/
//?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import java.awt.Rectangle
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class ClientBrowser(
    url: String,
    transparent: Boolean = true,
    val fps: Int = 60,
    val accelerate: Boolean = false
) {
    var url: String = url
        set(value) {
            field = value
            // A URL change is a full-document navigation. CEF keeps showing the LAST painted frame
            // (e.g. the main menu) until the new document paints, which flashes stale content for a
            // few frames. Mask until the new document's onLoadStart, then its first paint (see below).
            navigationPending = true
            navSawLoadStart = false
            navigationStartedAt = System.currentTimeMillis()
            browser.loadURL(value)
        }
        get() = browser.url
    var browser: MCEFBrowser
    // 1.21.5+ render bridge: WE own the frame texture and upload CEF's pixels through Minecraft's
    // GPU abstraction (BrowserOwnedTexture). mcef's raw-GL upload path is bypassed entirely — it
    // corrupted freshly-baked font-atlas glyphs (the p/q bug).
    //? if >=1.21.5 {
    private val ownedTexture = BrowserOwnedTexture()
    //?}
    private var lastRenderState: RenderState? = null
    private var renderWidth = 0
    private var renderHeight = 0
    private var browserWidth = 0
    private var browserHeight = 0
    // Read from CEF callback threads (expectedPaintSize/getScreenInfo) since the pump moved off
    // the render thread; written on the render thread in resize().
    @Volatile
    private var expectedTextureWidth = 0
    @Volatile
    private var expectedTextureHeight = 0
    private var contentScale = 1.0
    @Volatile
    private var deviceScale = 1.0
    // CEF DSF, locked at first resize to the startup monitor's scale — see the note in resize().
    private var lockedDeviceScale = 0.0

    /** The DSF this browser was created with (0 until the first resize). Used by BasicBrowser to
     *  lazily recreate the shared browser at native sharpness after a monitor-scale change. */
    val lockedScale: Double
        get() = lockedDeviceScale
    @Volatile
    private var waitingForResizeFrame = false
    private var lastZoomScale = -1.0
    // True from a URL change until the new document paints its first frame — see the url setter.
    @Volatile
    private var navigationPending = false
    private var navigationStartedAt = 0L
    // Set once the new navigation's onLoadStart fires. We only un-mask on a paint AFTER this, so a
    // residual paint of the OLD page (which arrives before onLoadStart) can't reveal stale content.
    @Volatile
    private var navSawLoadStart = false

    // Apply the webview scale as a CEF page zoom (zoomFactor = 1.2^zoomLevel), leaving the device-scale
    // factor untouched so the browser never gets stuck after a scale change. localhost origin keeps the
    // zoom across view reloads, so re-applying only when the value changes is enough.
    private fun applyZoom() {
        val scale = (ClientSettings.webViewScale.getValue() / 100.0).coerceAtLeast(0.1)
        if (scale == lastZoomScale) {
            return
        }
        lastZoomScale = scale
        browser.setZoomLevel(Math.log(scale) / Math.log(1.2))
    }

    init {
        val mcefBrowserSettings = MCEFBrowserSettings(fps, accelerate)
        logger.info(
            "Creating browser with target frame rate={}, acceleration={}, transparent={}",
            fps,
            accelerate,
            transparent
        )
        browser = ScaledBrowser(
            MCEF.INSTANCE.client,
            url,
            transparent,
            mcefBrowserSettings,
            ::currentDeviceScale,
            ::expectedPaintSize,
            ::handleBrowserFramePainted,
            ::handleBrowserFrameBuffer
        )
        browser.setCloseAllowed()
        browser.createImmediately()
        instances.add(this)
        //? if >=1.21.5 {
        if (shaders.isEmpty()){
            init()
        }
        // Frame uploads now go through Minecraft's GPU abstraction (BrowserOwnedTexture), but mcef
        // still performs residual raw GL around its own (now unused) renderer texture — creation via
        // renderer::initialize and deletion on close. Keep invalidating the state cache after each
        // CEF pump / mcef task so those raw calls can't poison it (see ExternalGlStateSync).
        ExternalGlStateSync.active = true
        //?}
    }

    /**
     * Browser frame delivery. Called on the render thread from mcef's flush (MCEF.update()):
     * CEF paints into a CPU mirror on its own message-loop thread, and the mirror is handed to us
     * here once per game frame. On 1.21.5+ we upload through Minecraft's encoder — no raw GL. On
     * older versions mcef's own upload path is kept (no glyph corruption there) and this callback
     * is never invoked.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun handleBrowserFrameBuffer(buffer: java.nio.ByteBuffer, width: Int, height: Int) {
        //? if >=1.21.5 {
        ownedTexture.upload(buffer, width, height)
        //?}
    }



    fun render(guiGraphics: GuiGraphics, width: Int, height: Int) {
        resize(width, height)
        applyZoom()
        reportRenderState()

        // Only skip when there is genuinely nothing to draw. We deliberately do NOT blank on
        // waitingForResizeFrame / size-mismatch: after a webview scale/size change the browser may
        // never repaint at the exact expected size (rounding / CEF clamping), which previously left
        // waitingForResizeFrame stuck true and the whole UI permanently invisible. Drawing the latest
        // painted texture (briefly stretched during a resize) is far better than disappearing.
        //? if >=1.21.5 {
        if (!ownedTexture.ready) {
            return
        }
        //?} else {
        /*if (!browser.renderer.isTextureReady || browser.renderer.isUnpainted) {
            return
        }*/
        //?}

        // During a navigation the renderer still holds the previous page's frame; skip drawing it so
        // the old view (e.g. the main menu) never flashes. Time-boxed so a missed paint can't wedge the
        // UI permanently blank (the same failure mode the waitingForResizeFrame note above warns about).
        if (navigationPending) {
            if (System.currentTimeMillis() - navigationStartedAt < NAVIGATION_BLANK_TIMEOUT_MS) {
                return
            }
            navigationPending = false
        }

        //? if >=1.21.5 {
        val textureSetup = ownedTexture.setup
        // The owned texture stores CEF's BGRA bytes as-is; the bgra shader swaps R/B at draw time.
        val pipeline: RenderPipeline? = getShader("pipeline/jcef/bgra_blurred_texture")
        val guiGraphicsAccessor = guiGraphics as IGuiGraphics
        guiGraphicsAccessor.fpsmasterGuiRenderState().submitGuiElement(
            TexQuadGuiElementRenderState(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                0f,
                0f,
                1f,
                1f,
                -1,
                pipeline,
                textureSetup,
                guiGraphics.pose(),
                guiGraphicsAccessor.fpsmasterScissorArea(),
                createBounds(0, 0, width, height)
            )
        )
        //?}
        // 1.20.5..1.21.4 immediate mode: Tesselator.begin() returns the builder, addVertex/setUv,
        // and BufferUploader.drawWithShader(buildOrThrow()). Distinct from the older 1.20.1 API below.
        //? if >=1.20.5 && <1.21.5 {
        /*RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderTexture(0, browser.renderer.textureId)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        val matrix = guiGraphics.pose().last().pose()
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        buffer.addVertex(matrix, 0f, height.toFloat(), 0f).setUv(0f, 1f)
        buffer.addVertex(matrix, width.toFloat(), height.toFloat(), 0f).setUv(1f, 1f)
        buffer.addVertex(matrix, width.toFloat(), 0f, 0f).setUv(1f, 0f)
        buffer.addVertex(matrix, 0f, 0f, 0f).setUv(0f, 0f)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
        RenderSystem.disableBlend()*/
        //?}
        // 1.20.1 (and older) immediate mode: tesselator.builder + vertex().uv().endVertex() + tesselator.end().
        //? if <1.20.5 {
        /*RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderTexture(0, browser.renderer.textureId)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        val matrix = guiGraphics.pose().last().pose()
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.builder
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        buffer.vertex(matrix, 0f, height.toFloat(), 0f).uv(0f, 1f).endVertex()
        buffer.vertex(matrix, width.toFloat(), height.toFloat(), 0f).uv(1f, 1f).endVertex()
        buffer.vertex(matrix, width.toFloat(), 0f, 0f).uv(1f, 0f).endVertex()
        buffer.vertex(matrix, 0f, 0f, 0f).uv(0f, 0f).endVertex()
        tesselator.end()
        RenderSystem.disableBlend()*/
        //?}
    }

    //? if >=1.20 {
    fun createBounds(x: Int, y: Int, w: Int, h: Int): ScreenRectangle {
        return ScreenRectangle(x, y, w, h)
    }
    //?}

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }

        val window = mc.window
        val framebufferScaleX = window.width.toDouble() / window.guiScaledWidth.coerceAtLeast(1)
        val framebufferScaleY = window.height.toDouble() / window.guiScaledHeight.coerceAtLeast(1)
        val framebufferRenderWidth = (width * framebufferScaleX).roundToInt().coerceAtLeast(1)
        val framebufferRenderHeight = (height * framebufferScaleY).roundToInt().coerceAtLeast(1)
        // The page's CSS viewport follows the window's LOGICAL (point) size: framebuffer pixels
        // divided by the monitor's actual content scale (2.0 on Retina, 1.0 on standard displays),
        // queried live from GLFW. Previously this divided by a hard-coded 2.0, which halved the CSS
        // viewport on 1x displays — the same window showed a comically zoomed-in UI there. Deriving
        // the divisor from the real content scale keeps the web UI the same apparent size for the
        // same window size on every monitor (and independent of MC's gui-scale).
        //
        // The CEF device-scale factor, however, is LOCKED to the scale of the monitor the game
        // started on: this jcef predates NotifyScreenInfoChanged and its native side reads screen
        // info only at browser creation (verified: getScreenInfo is never re-queried after a
        // cross-monitor drag), so any mid-session DSF change permanently wedges the compositor.
        // With a locked DSF only the view SIZE changes when dragging across monitors — the same
        // stable path as plain window resizing. Cost: dragging to a differently-scaled monitor
        // renders slightly over- or under-sampled (scaled in hardware), which is a fine trade for
        // never freezing. webViewScale remains a user-preference CEF zoom on top (see applyZoom).
        val liveScale = currentMonitorContentScale()
        val nextContentScale = if (liveScale > 0.0) {
            liveScale
        } else {
            BASE_WEBVIEW_SCALE // Degenerate window state (minimized); keep the previous behaviour.
        }
        if (lockedDeviceScale == 0.0) {
            lockedDeviceScale = nextContentScale
        }
        val nextBrowserWidth = (framebufferRenderWidth / nextContentScale).roundToInt().coerceAtLeast(1)
        val nextBrowserHeight = (framebufferRenderHeight / nextContentScale).roundToInt().coerceAtLeast(1)
        val nextDeviceScale = lockedDeviceScale
        val nextExpectedTextureWidth = (nextBrowserWidth * nextDeviceScale).roundToInt().coerceAtLeast(1)
        val nextExpectedTextureHeight = (nextBrowserHeight * nextDeviceScale).roundToInt().coerceAtLeast(1)

        renderWidth = width
        renderHeight = height
        if (
            browserWidth == nextBrowserWidth &&
            browserHeight == nextBrowserHeight &&
            contentScale == nextContentScale &&
            deviceScale == nextDeviceScale &&
            expectedTextureWidth == nextExpectedTextureWidth &&
            expectedTextureHeight == nextExpectedTextureHeight
        ) {
            return
        }

        browserWidth = nextBrowserWidth
        browserHeight = nextBrowserHeight
        contentScale = nextContentScale
        deviceScale = nextDeviceScale
        expectedTextureWidth = nextExpectedTextureWidth
        expectedTextureHeight = nextExpectedTextureHeight
        waitingForResizeFrame = true
        logger.info(
            "Browser resize: render={}x{}, browserView={}x{}, expectedTexture={}x{}, framebufferRender={}x{}, framebuffer={}x{}, gui={}x{}, guiScale={}, contentScale={}, deviceScale={}",
            renderWidth,
            renderHeight,
            browserWidth,
            browserHeight,
            expectedTextureWidth,
            expectedTextureHeight,
            framebufferRenderWidth,
            framebufferRenderHeight,
            mc.window.width,
            mc.window.height,
            mc.window.guiScaledWidth,
            mc.window.guiScaledHeight,
            mc.window.guiScale,
            contentScale,
            deviceScale
        )
        browser.resize(browserWidth, browserHeight)
        browser.clear()
        //? if >=1.21.5 {
        // resize/clear can touch the browser texture with raw GL synchronously (we may be mid
        // render-pass here); realign GlStateManager's cache with GL reality before continuing.
        ExternalGlStateSync.resync()
        //?}
    }

    private fun handleBrowserFramePainted(width: Int, height: Int) {
        // First real paint AFTER the new navigation started = the new document's content. Only now is it
        // safe to stop masking: the old page's residual paints arrive before navSawLoadStart is set.
        if (navigationPending && navSawLoadStart) {
            navigationPending = false
        }
        if (width == expectedTextureWidth && height == expectedTextureHeight) {
            waitingForResizeFrame = false
        }
    }

    /** Global LoadHandler forwards here (matched by CefBrowser identity) when a load begins. */
    private fun handleNavLoadStart() {
        if (navigationPending) {
            navSawLoadStart = true
        }
    }

    private fun expectedPaintSize(): Pair<Int, Int> {
        return expectedTextureWidth to expectedTextureHeight
    }

    private fun currentDeviceScale(): Double {
        return deviceScale
    }

    private fun mouseX(x: Double): Int {
        if (renderWidth <= 0) {
            return x.toInt()
        }
        return (x * browserWidth / renderWidth).toInt()
    }

    private fun mouseY(y: Double): Int {
        if (renderHeight <= 0) {
            return y.toInt()
        }
        return (y * browserHeight / renderHeight).toInt()
    }

    private fun reportRenderState() {
        val renderer = browser.renderer
        val state = RenderState(
            accelerated = renderer.isAccelerated,
            bgra = renderer.isBGRA,
            textureReady = renderer.isTextureReady,
            unpainted = renderer.isUnpainted,
            textureWidth = renderer.textureWidth,
            textureHeight = renderer.textureHeight,
            hasTextureSetup = renderer.textureId != 0,
            renderWidth = renderWidth,
            renderHeight = renderHeight,
            browserWidth = browserWidth,
            browserHeight = browserHeight,
            expectedTextureWidth = expectedTextureWidth,
            expectedTextureHeight = expectedTextureHeight,
            contentScale = contentScale,
            deviceScale = deviceScale,
            waitingForResizeFrame = waitingForResizeFrame
        )

        if (state == lastRenderState) {
            return
        }

        logger.info(
            "Browser render state: requestedAcceleration={}, accelerated={}, bgra={}, textureReady={}, unpainted={}, waitingForResizeFrame={}, textureSize={}x{}, expectedTexture={}x{}, render={}x{}, browserView={}x{}, contentScale={}, deviceScale={}, textureSetup={}",
            accelerate,
            state.accelerated,
            state.bgra,
            state.textureReady,
            state.unpainted,
            state.waitingForResizeFrame,
            state.textureWidth,
            state.textureHeight,
            state.expectedTextureWidth,
            state.expectedTextureHeight,
            state.renderWidth,
            state.renderHeight,
            state.browserWidth,
            state.browserHeight,
            state.contentScale,
            state.deviceScale,
            state.hasTextureSetup
        )
        lastRenderState = state
    }

    fun mouseClicked(x: Double, y: Double, button: Int) {
        // CEF off-screen hit-testing uses the browser's last known cursor position. On older versions
        // (<=1.20.1) Screen.mouseMoved is not dispatched the way it is on 1.21.x, so without this the
        // browser still thinks the cursor is at (0,0) and the press lands in the top-left corner —
        // every menu button appears dead even though the page renders. Sync the cursor to the click
        // point first so the press hit-tests the element actually under the mouse. Harmless on all
        // versions (1.21.x already moved the cursor via mouseMoved).
        browser.sendMouseMove(mouseX(x), mouseY(y))
        browser.sendMousePress(mouseX(x), mouseY(y), button)
        browser.setFocus(true)
        // Optional in-game IME positioning: enable IME and anchor the candidate box where the user
        // clicked (likely into a web input). No-op on versions without the GLFW preedit API.
        ImeSupport.setEnabled(true)
        ImeSupport.positionAtCursor()
    }

    fun mouseReleased(x: Double, y: Double, button: Int) {
        // Match mouseClicked: keep CEF's cursor at the release point so the click completes on the
        // correct element (a press+release at mismatched positions can be treated as a drag/cancel).
        browser.sendMouseMove(mouseX(x), mouseY(y))
        browser.sendMouseRelease(mouseX(x), mouseY(y), button)
        browser.setFocus(true)
    }

    fun sendMouseMove(mouseX: Double, mouseY: Double) {
        browser.sendMouseMove(mouseX(mouseX), mouseY(mouseY))
        browser.setFocus(true)
    }

    fun sendMouseWheel(mouseX: Double, mouseY: Double, scrollY: Double) {
        browser.sendMouseWheel(mouseX(mouseX), mouseY(mouseY), scrollY)
        browser.setFocus(true)
    }

    fun sendKeyPress(key: Int, toLong: Long, modifiers: Int) {
        browser.sendKeyPress(key, toLong, modifiers)
        browser.setFocus(true)
        // Keep the IME candidate box anchored as the user composes (the OS re-queries the rect).
        ImeSupport.positionAtCursor()
    }

    fun sendKeyRelease(key: Int, toLong: Long, modifiers: Int) {
        browser.sendKeyRelease(key, toLong, modifiers)
        browser.setFocus(true)
    }

    fun sendKeyTyped(ch: Char, modifiers: Int) {
        browser.sendKeyTyped(ch, modifiers)
        browser.setFocus(true)
    }

    /**
     * Insert already-composed text (e.g. IME-committed Chinese) into the focused web input.
     *
     * The vendored java-cef has no OSR IME bindings, and CEF KEY_TYPE char events do not reliably
     * insert non-ASCII text on macOS OSR. GLFW still delivers the *committed* IME text to charTyped,
     * so instead of routing it through a CEF key event we inject it straight into the active element
     * via execCommand('insertText'), which fires proper beforeinput/input events (so controlled React
     * inputs update correctly).
     */
    fun insertText(text: String) {
        if (text.isEmpty()) return
        val literal = jsString(text)
        val js = """
            (function(){
              try {
                var t = $literal;
                var el = document.activeElement;
                if (!el) return;
                var editable = el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable;
                if (!editable) return;
                if (document.execCommand && document.execCommand('insertText', false, t)) return;
                if (typeof el.value === 'string' && el.selectionStart != null) {
                  var s = el.selectionStart, e = el.selectionEnd;
                  el.value = el.value.slice(0, s) + t + el.value.slice(e);
                  var p = s + t.length;
                  el.selectionStart = el.selectionEnd = p;
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                }
              } catch (err) {}
            })();
        """.trimIndent()
        browser.executeJavaScript(js, browser.url, 0)
        browser.setFocus(true)
    }

    /** ASCII-safe JS string literal: \\u-escapes every non-ASCII char so encoding can't corrupt it. */
    private fun jsString(text: String): String {
        val sb = StringBuilder(text.length + 2)
        sb.append('"')
        for (c in text) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20 || c.code > 0x7e) {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun close() {
        ImeSupport.setEnabled(false)
        ImeSupport.reset()
        browser.close()
        //? if >=1.21.5 {
        ownedTexture.close()
        //?}
    }

    private data class RenderState(
        val accelerated: Boolean,
        val bgra: Boolean,
        val textureReady: Boolean,
        val unpainted: Boolean,
        val textureWidth: Int,
        val textureHeight: Int,
        val hasTextureSetup: Boolean,
        val renderWidth: Int,
        val renderHeight: Int,
        val browserWidth: Int,
        val browserHeight: Int,
        val expectedTextureWidth: Int,
        val expectedTextureHeight: Int,
        val contentScale: Double,
        val deviceScale: Double,
        val waitingForResizeFrame: Boolean
    )

    companion object {
        // Fallback content scale for degenerate window states only — the live value is derived from
        // framebuffer / GLFW window size in resize().
        private const val BASE_WEBVIEW_SCALE = 2.0

        /**
         * The content scale of the monitor the game window is currently on (framebuffer pixels /
         * GLFW window points): 2.0 on Retina, 1.0 on standard displays. 0.0 when the window is in a
         * degenerate state (minimized).
         */
        fun currentMonitorContentScale(): Double {
            val window = mc.window
            //? if >=1.21.11 {
            val windowHandle = window.handle()
            //?} else {
            /*val windowHandle = window.window*/
            //?}
            val logicalWidth = IntArray(1)
            val logicalHeight = IntArray(1)
            GLFW.glfwGetWindowSize(windowHandle, logicalWidth, logicalHeight)
            return if (logicalWidth[0] > 0) {
                (window.width.toDouble() / logicalWidth[0]).coerceIn(0.5, 4.0)
            } else {
                0.0
            }
        }
        // Safety cap on how long render() will blank while waiting for a post-navigation paint.
        private const val NAVIGATION_BLANK_TIMEOUT_MS = 2000L

        private val instances = java.util.concurrent.CopyOnWriteArrayList<ClientBrowser>()

        /** Routed from the global [LoadHandler] onLoadStart, matched to its owning browser by identity. */
        fun onLoadStart(cefBrowser: CefBrowser?, frame: CefFrame?) {
            if (cefBrowser == null || frame?.isMain != true) {
                return
            }
            instances.firstOrNull { it.browser === cefBrowser }?.handleNavLoadStart()
        }
    }

    private class ScaledBrowser(
        client: MCEFClient,
        url: String,
        transparent: Boolean,
        browserSettings: MCEFBrowserSettings,
        private val currentDeviceScale: () -> Double,
        private val expectedPaintSize: () -> Pair<Int, Int>,
        private val onFramePainted: (Int, Int) -> Unit,
        private val onFrameUpload: (ByteBuffer, Int, Int) -> Unit
    ) : MCEFBrowser(client, url, transparent, browserSettings) {
        private var lastAcceleratedPaintState: AcceleratedPaintState? = null

        // NOTE: this jcef's native side reads screen info ONLY at browser creation (it predates
        // NotifyScreenInfoChanged and never re-queries after WasResized) — which is why the DSF we
        // report here must stay constant for the browser's lifetime (see resize()).
        override fun getScreenInfo(browser: CefBrowser, screenInfo: CefScreenInfo): Boolean {
            val viewBounds = getViewRect(browser).bounds
            val screenBounds = Rectangle(0, 0, viewBounds.width, viewBounds.height)
            screenInfo.Set(currentDeviceScale(), 32, 8, false, screenBounds, screenBounds)
            return true
        }

        override fun onAcceleratedPaint(
            browser: CefBrowser,
            popup: Boolean,
            dirtyRects: Array<Rectangle>,
            info: CefAcceleratedPaintInfo
        ) {
            val expectedSize = expectedPaintSize()
            reportAcceleratedPaintState(popup, dirtyRects, info, expectedSize)
            val textureSize = acceleratedTextureSize(dirtyRects, expectedSize)

            if (!popup && textureSize != null) {
                // The GL import happens on the render thread when mcef flushes this queue entry.
                queueAcceleratedFrame(info, textureSize.first, textureSize.second)
                onFramePainted(textureSize.first, textureSize.second)
                return
            }

            super.onAcceleratedPaint(browser, popup, dirtyRects, info)
            if (
                !popup &&
                dirtyRects.isNotEmpty() &&
                renderer.textureWidth == info.width &&
                renderer.textureHeight == info.height
            ) {
                onFramePainted(info.width, info.height)
            }
        }

        private fun acceleratedTextureSize(
            dirtyRects: Array<Rectangle>,
            expectedSize: Pair<Int, Int>
        ): Pair<Int, Int>? {
            if (dirtyRects.isEmpty()) {
                return null
            }

            val expectedWidth = expectedSize.first
            val expectedHeight = expectedSize.second
            if (expectedWidth <= 1 || expectedHeight <= 1) {
                return null
            }

            val firstDirtyRect = dirtyRects[0]
            val fullExpectedFrame = firstDirtyRect.x == 0 &&
                firstDirtyRect.y == 0 &&
                firstDirtyRect.width == expectedWidth &&
                firstDirtyRect.height == expectedHeight
            if (fullExpectedFrame) {
                return expectedSize
            }

            val rendererAlreadyHasExpectedTexture = renderer.textureWidth == expectedWidth &&
                renderer.textureHeight == expectedHeight
            if (!rendererAlreadyHasExpectedTexture) {
                return null
            }

            val allDirtyRectsInsideExpectedTexture = dirtyRects.all { rect ->
                rect.x >= 0 &&
                    rect.y >= 0 &&
                    rect.x + rect.width <= expectedWidth &&
                    rect.y + rect.height <= expectedHeight
            }
            return if (allDirtyRectsInsideExpectedTexture) expectedSize else null
        }

        /**
         * Render-thread frame hand-off from mcef's flush. CEF paints (on its message-loop thread)
         * are composited into a CPU mirror inside MCEFBrowser — including malformed-frame guards —
         * and land here at most once per game frame.
         */
        override fun uploadFrame(
            frame: ByteBuffer,
            width: Int,
            height: Int,
            dirty: Rectangle,
            fullUpload: Boolean
        ) {
            //? if >=1.21.5 {
            // Bypass mcef's raw-GL upload entirely (it corrupted freshly-baked font-atlas glyphs —
            // the p/q bug) and hand the frame to BrowserOwnedTexture, which uploads through
            // Minecraft's own GPU encoder.
            try {
                onFrameUpload(frame, width, height)
            } catch (t: Throwable) {
                // Never let an upload failure escape into mcef's flush loop — log and drop the frame.
                logger.error("Browser frame upload failed ({}x{})", width, height, t)
                return
            }
            //?} else {
            /*super.uploadFrame(frame, width, height, dirty, fullUpload)*/
            //?}
            onFramePainted(width, height)
        }

        //? if >=1.21.5 {
        /**
         * Native dropdown overlays are not composited on the owned-texture path — the web UI draws
         * its own dropdowns. (The default would write into mcef's unused renderer texture.)
         */
        override fun uploadPopup(popup: ByteBuffer, popupRect: Rectangle, frameWidth: Int, frameHeight: Int) {
            // no-op
        }
        //?}

        private fun reportAcceleratedPaintState(
            popup: Boolean,
            dirtyRects: Array<Rectangle>,
            info: CefAcceleratedPaintInfo,
            expectedSize: Pair<Int, Int>
        ) {
            val firstDirtyRect = dirtyRects.firstOrNull()
            val state = AcceleratedPaintState(
                popup = popup,
                width = info.width,
                height = info.height,
                dirtyX = firstDirtyRect?.x ?: -1,
                dirtyY = firstDirtyRect?.y ?: -1,
                dirtyWidth = firstDirtyRect?.width ?: 0,
                dirtyHeight = firstDirtyRect?.height ?: 0,
                expectedWidth = expectedSize.first,
                expectedHeight = expectedSize.second
            )

            if (state == lastAcceleratedPaintState) {
                return
            }

            logger.info(
                "Browser accelerated paint: popup={}, size={}x{}, dirty={}x{}+{}+{}, expectedTexture={}x{}",
                state.popup,
                state.width,
                state.height,
                state.dirtyWidth,
                state.dirtyHeight,
                state.dirtyX,
                state.dirtyY,
                state.expectedWidth,
                state.expectedHeight
            )
            lastAcceleratedPaintState = state
        }

        private data class AcceleratedPaintState(
            val popup: Boolean,
            val width: Int,
            val height: Int,
            val dirtyX: Int,
            val dirtyY: Int,
            val dirtyWidth: Int,
            val dirtyHeight: Int,
            val expectedWidth: Int,
            val expectedHeight: Int
        )

    }
}
