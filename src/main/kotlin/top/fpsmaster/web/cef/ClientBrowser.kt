package top.fpsmaster.web.cef

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowser
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowserSettings
import net.ccbluex.liquidbounce.mcef.cef.MCEFClient
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import org.cef.browser.CefBrowser
import org.cef.handler.CefAcceleratedPaintInfo
import org.cef.handler.CefScreenInfo
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.mixin.interfaces.IGuiGraphics
import top.fpsmaster.module.impl.auxiliary.ClientSettings
import top.fpsmaster.render.shaders.getShader
import top.fpsmaster.render.shaders.init
import top.fpsmaster.render.shaders.shaders
import top.fpsmaster.web.TexQuadGuiElementRenderState
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
        if (shaders.isEmpty()){
            init()
        }
    }



    fun render(guiGraphics: GuiGraphics, width: Int, height: Int) {
        resize(width, height)
        reportRenderState()

        if (
            waitingForResizeFrame ||
            !browser.renderer.isTextureReady ||
            browser.renderer.isUnpainted ||
            !isExpectedTextureSize()
        ) {
            return
        }

        val textureSetup = browser.renderer.textureSetup
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
    }

    fun createBounds(x: Int, y: Int, w: Int, h: Int): ScreenRectangle {
        return ScreenRectangle(x, y, w, h)
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }

        val nextContentScale = (ClientSettings.webViewScale.getValue() / 100.0).coerceAtLeast(0.01)
        val nextBrowserWidth = (width / nextContentScale).roundToInt().coerceAtLeast(1)
        val nextBrowserHeight = (height / nextContentScale).roundToInt().coerceAtLeast(1)
        val nextDeviceScale = mc.window.guiScale.coerceAtLeast(1).toDouble() * nextContentScale
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
            "Browser resize: render={}x{}, browserView={}x{}, expectedTexture={}x{}, framebuffer={}x{}, gui={}x{}, guiScale={}, contentScale={}, deviceScale={}",
            renderWidth,
            renderHeight,
            browserWidth,
            browserHeight,
            expectedTextureWidth,
            expectedTextureHeight,
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

    private fun isExpectedTextureSize(): Boolean {
        return browser.renderer.textureWidth == expectedTextureWidth &&
            browser.renderer.textureHeight == expectedTextureHeight
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
            hasTextureSetup = renderer.textureSetup != null,
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
    }

    fun sendKeyRelease(key: Int, toLong: Long, modifiers: Int) {
        browser.sendKeyRelease(key, toLong, modifiers)
        browser.setFocus(true)
    }

    fun sendKeyTyped(ch: Char, modifiers: Int) {
        browser.sendKeyTyped(ch, modifiers)
        browser.setFocus(true)
    }

    fun close() {
        browser.close()
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
            super.onPaint(browser, popup, dirtyRects, buffer, width, height)
            if (
                !popup &&
                dirtyRects.isNotEmpty() &&
                renderer.textureWidth == width &&
                renderer.textureHeight == height
            ) {
                onFramePainted(width, height)
            }
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
