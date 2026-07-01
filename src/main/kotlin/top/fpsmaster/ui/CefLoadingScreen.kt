package top.fpsmaster.ui

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import top.fpsmaster.Client
import top.fpsmaster.mc

/**
 * Native (non-CEF) loading screen shown at startup while the JCEF runtime downloads and initializes.
 * It renders a simple progress bar and drives [Client.pumpCefInit] on the render thread, then hands off
 * to [next] once CEF is ready. Because it uses no webview it can never itself freeze on CEF.
 */
class CefLoadingScreen(private val next: () -> Screen) : Screen(Component.literal("FPSMaster")) {
    private var failedSince = 0L

    //? if >=1.20 {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    //?} else {
    /*override fun render(poseStack: com.mojang.blaze3d.vertex.PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val guiGraphics = GuiGraphics(poseStack)*/
    //?}
        //? if >=1.20 {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        //?} else {
        /*super.render(poseStack, mouseX, mouseY, partialTick)*/
        //?}

        guiGraphics.fill(0, 0, width, height, 0xE8080810.toInt())

        // Drive CEF init on the render thread; hand off to the next screen when ready.
        Client.pumpCefInit()
        if (Client.cefState == Client.CefState.READY) {
            mc.setScreen(next())
            return
        }

        val centerX = width / 2
        val centerY = height / 2
        guiGraphics.drawCenteredString(mc.font, "FPSMaster Nova", centerX, centerY - 46, 0xFFFFFFFF.toInt())

        if (Client.cefState == Client.CefState.FAILED) {
            if (failedSince == 0L) {
                failedSince = System.currentTimeMillis()
            }
            guiGraphics.drawCenteredString(mc.font, Client.cefFailureMessage ?: "CEF 加载失败", centerX, centerY - 6, 0xFFFF6B6B.toInt())
            guiGraphics.drawCenteredString(mc.font, "将使用原版界面…", centerX, centerY + 8, 0xFFAAAAAA.toInt())
            if (System.currentTimeMillis() - failedSince > 3000L) {
                mc.setScreen(TitleScreen())
            }
            return
        }

        val barWidth = 240
        val barX = centerX - barWidth / 2
        val barY = centerY - 3
        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + 7, 0xFF000000.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + 6, 0xFF1E242C.toInt())
        val progress = Client.cefDownloadProgress.coerceIn(0f, 1f)
        guiGraphics.fill(barX, barY, barX + (barWidth * progress).toInt(), barY + 6, 0xFF6366F1.toInt())

        val label = if (Client.cefState == Client.CefState.DOWNLOADING) {
            "正在下载运行库… ${(progress * 100).toInt()}%"
        } else {
            "正在初始化…"
        }
        guiGraphics.drawCenteredString(mc.font, label, centerX, barY + 16, 0xFFC8D0DA.toInt())
        if (Client.cefDownloadStage.isNotEmpty()) {
            guiGraphics.drawCenteredString(mc.font, Client.cefDownloadStage, centerX, barY + 28, 0xFF6B7580.toInt())
        }
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun isPauseScreen(): Boolean = false
}
