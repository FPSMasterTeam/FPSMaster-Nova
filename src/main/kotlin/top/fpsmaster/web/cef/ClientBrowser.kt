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
            browser.loadURL(value)
        }
        get() = browser.url
    var browser: MCEFBrowser
    // 1.21.5+ render bridge: wraps the raw GL texture id from mcef-nova into a TextureSetup.
    //? if >=1.21.5 {
    private val directTexture = BrowserDirectTexture()
    //?}
    private var lastRenderState: RenderState? = null
    private var renderWidth = 0
    private var renderHeight = 0
    private var browserWidth = 0
    private var browserHeight = 0
    private var expectedTextureWidth = 0
    private var expectedTextureHeight = 0
    private var contentScale = 1.0
    private var deviceScale = 1.0
    private var waitingForResizeFrame = false

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
            ::handleBrowserFramePainted
        )
        browser.setCloseAllowed()
        browser.createImmediately()
        //? if >=1.21.5 {
        if (shaders.isEmpty()){
            init()
        }
        //?}
    }



    fun render(guiGraphics: GuiGraphics, width: Int, height: Int) {
        resize(width, height)
        reportRenderState()

        // Only skip when there is genuinely nothing to draw. We deliberately do NOT blank on
        // waitingForResizeFrame / size-mismatch: after a webview scale/size change the browser may
        // never repaint at the exact expected size (rounding / CEF clamping), which previously left
        // waitingForResizeFrame stuck true and the whole UI permanently invisible. Drawing the latest
        // painted texture (briefly stretched during a resize) is far better than disappearing.
        if (!browser.renderer.isTextureReady || browser.renderer.isUnpainted) {
            return
        }

        //? if >=1.21.5 {
        val textureSetup = directTexture.wrap(
            browser.renderer.textureId,
            browser.renderer.textureWidth,
            browser.renderer.textureHeight
        )
        val bgra = browser.renderer.isBGRA
        var pipeline: RenderPipeline? = null
        pipeline = if (bgra) {
            getShader("pipeline/jcef/bgra_blurred_texture");
        } else {
            getShader("pipeline/jcef/texture");
        }
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
        val nextContentScale = (BASE_WEBVIEW_SCALE * ClientSettings.webViewScale.getValue() / 100.0).coerceAtLeast(0.01)
        val nextBrowserWidth = (framebufferRenderWidth / nextContentScale).roundToInt().coerceAtLeast(1)
        val nextBrowserHeight = (framebufferRenderHeight / nextContentScale).roundToInt().coerceAtLeast(1)
        val nextDeviceScale = nextContentScale
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
    }

    private fun handleBrowserFramePainted(width: Int, height: Int) {
        if (width == expectedTextureWidth && height == expectedTextureHeight) {
            waitingForResizeFrame = false
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
        browser.sendMousePress(mouseX(x), mouseY(y), button)
        browser.setFocus(true)
        // Optional in-game IME positioning: enable IME and anchor the candidate box where the user
        // clicked (likely into a web input). No-op on versions without the GLFW preedit API.
        ImeSupport.setEnabled(true)
        ImeSupport.positionAtCursor()
    }

    fun mouseReleased(x: Double, y: Double, button: Int) {
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
        directTexture.close()
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
        private const val BASE_WEBVIEW_SCALE = 2.0
    }

    private class ScaledBrowser(
        client: MCEFClient,
        url: String,
        transparent: Boolean,
        browserSettings: MCEFBrowserSettings,
        private val currentDeviceScale: () -> Double,
        private val expectedPaintSize: () -> Pair<Int, Int>,
        private val onFramePainted: (Int, Int) -> Unit
    ) : MCEFBrowser(client, url, transparent, browserSettings) {
        private var lastAcceleratedPaintState: AcceleratedPaintState? = null
        private var lastPaintRejection: String? = null

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
                paintAcceleratedTexture(info, textureSize.first, textureSize.second)
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

        override fun onPaint(
            browser: CefBrowser,
            popup: Boolean,
            dirtyRects: Array<Rectangle>,
            buffer: ByteBuffer,
            width: Int,
            height: Int
        ) {
            // Defensive guard: MCEFBrowser.onPaint copies `width * height * 4` bytes out of the
            // CEF buffer via an unchecked native memCopy. A malformed frame (non-positive size, or
            // a buffer shorter than the advertised frame) makes that copy read past the source and
            // crashes the JVM with a SIGBUS that no try/catch can recover from. Skip such frames so
            // a bad paint degrades to a dropped frame instead of taking the whole client down.
            if (!isPaintBufferValid(popup, buffer, width, height)) {
                return
            }
            try {
                super.onPaint(browser, popup, dirtyRects, buffer, width, height)
            } catch (throwable: RuntimeException) {
                logBrowserPaintRejection(popup, width, height, buffer.remaining().toLong(), "paint threw ${throwable.javaClass.simpleName}")
                return
            }
            if (
                !popup &&
                dirtyRects.isNotEmpty() &&
                renderer.textureWidth == width &&
                renderer.textureHeight == height
            ) {
                onFramePainted(width, height)
            }
        }

        private fun isPaintBufferValid(popup: Boolean, buffer: ByteBuffer, width: Int, height: Int): Boolean {
            if (width <= 0 || height <= 0) {
                logBrowserPaintRejection(popup, width, height, buffer.remaining().toLong(), "non-positive dimensions")
                return false
            }
            val requiredBytes = width.toLong() * height.toLong() * BYTES_PER_PIXEL
            val availableBytes = buffer.remaining().toLong()
            if (availableBytes < requiredBytes) {
                logBrowserPaintRejection(popup, width, height, availableBytes, "buffer shorter than frame (need $requiredBytes)")
                return false
            }
            return true
        }

        private fun logBrowserPaintRejection(popup: Boolean, width: Int, height: Int, availableBytes: Long, reason: String) {
            val signature = "$popup:$width:$height:$availableBytes:$reason"
            if (signature == lastPaintRejection) {
                return
            }
            lastPaintRejection = signature
            logger.warn(
                "Skipped browser paint frame to avoid out-of-bounds copy: popup={}, size={}x{}, bufferBytes={}, reason={}",
                popup,
                width,
                height,
                availableBytes,
                reason
            )
        }

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

        private fun paintAcceleratedTexture(info: CefAcceleratedPaintInfo, width: Int, height: Int) {
            try {
                if (lastWidthField.getInt(this) != width || lastHeightField.getInt(this) != height) {
                    lastWidthField.setInt(this, width)
                    lastHeightField.setInt(this, height)
                }
                rendererOnAcceleratedPaintMethod.invoke(renderer, info, width, height)
            } catch (exception: ReflectiveOperationException) {
                logger.warn("Failed to paint accelerated browser texture", exception)
            }
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

        companion object {
            private const val BYTES_PER_PIXEL = 4L

            private val lastWidthField = MCEFBrowser::class.java.getDeclaredField("lastWidth").apply {
                isAccessible = true
            }
            private val lastHeightField = MCEFBrowser::class.java.getDeclaredField("lastHeight").apply {
                isAccessible = true
            }
            private val rendererOnAcceleratedPaintMethod = Class
                .forName("net.ccbluex.liquidbounce.mcef.cef.MCEFRenderer")
                .getDeclaredMethod(
                    "onAcceleratedPaint",
                    CefAcceleratedPaintInfo::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                .apply {
                    isAccessible = true
                }
        }
    }
}
