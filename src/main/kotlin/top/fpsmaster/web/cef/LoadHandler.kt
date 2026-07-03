package top.fpsmaster.web.cef

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.network.CefRequest
import top.fpsmaster.logger

class LoadHandler: CefLoadHandler {
    override fun onLoadingStateChange(
        p0: CefBrowser?,
        p1: Boolean,
        p2: Boolean,
        p3: Boolean
    ) {
        logger.info("onLoadingStateChange")
    }

    override fun onLoadStart(
        p0: CefBrowser?,
        p1: CefFrame?,
        p2: CefRequest.TransitionType?
    ) {
        logger.info("onLoadStart")
        // Let the owning ClientBrowser know a navigation committed, so it can stop masking the previous
        // view's stale frame on the next real paint (fixes the main-menu flash on view switches).
        ClientBrowser.onLoadStart(p0, p1)
    }

    override fun onLoadEnd(p0: CefBrowser?, p1: CefFrame?, p2: Int) {
        logger.info("onLoadEnd")
    }

    override fun onLoadError(
        p0: CefBrowser?,
        p1: CefFrame?,
        p2: CefLoadHandler.ErrorCode?,
        p3: String?,
        p4: String?
    ) {
        logger.info("onLoadError $p3 $p4")
    }

}
