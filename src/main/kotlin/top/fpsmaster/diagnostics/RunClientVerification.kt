package top.fpsmaster.diagnostics

import net.minecraft.client.Screenshot
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.screenCompat
import top.fpsmaster.setScreenCompat
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Opt-in acceptance observer. With verify.auto it also drives the UI through the same entry points the buttons call. */
object RunClientVerification {
    private val output = System.getProperty("fpsmaster.verify.shots")?.let(Path::of)
    private val version = System.getProperty("fpsmaster.verify.version").orEmpty()
    private val run = System.getProperty("fpsmaster.verify.run").orEmpty()
    private val soak = System.getProperty("fpsmaster.verify.soakSeconds", "180").toLong()
    private val auto = System.getProperty("fpsmaster.verify.auto") == "true"
    private val stages = listOf("main-menu", "create-world", "world", "clickgui", "cosmetics", "settings", "returned-menu")
    private val filenames = listOf("01-menu", "02-create-world", "03-world", "04-clickgui", "05-cosmetics", "06-settings", "07-returned-menu")
    private val completed = ConcurrentHashMap.newKeySet<String>()
    private var initialized = false
    private var failed = false
    private var pending = false
    private var current = ""
    private var accumulated = 0L
    private var lastFrame = 0L
    private var step = 0
    private var driveAt = 0L
    private var stallStep = -1
    private var stepAt = 0L
    private var prevStep = -1

    fun frame() {
        val directory = output ?: return
        if (failed || pending) return
        if (!initialized) {
            val expected = System.getProperty("fpsmaster.verify.gameDir")?.let(Path::of)
            check(expected != null && mc.gameDirectory.toPath().toRealPath() == expected.toRealPath()) {
                "Verification gameDir does not match the isolated directory"
            }
            Files.createDirectories(directory)
            logger.info("nova-verify ready run={} version={} smoke={} gameDir={}",
                run, version, Smoke.ENABLED, expected)
            initialized = true
        }
        drive()
        // Do not capture loading overlays, even if the menu's Screen already exists beneath them.
        //? if >=26 {
        /*val loading = mc.gui.overlay() != null
        *///?} else {
        val loading = mc.overlay != null
        //?}
        val screen = mc.screenCompat?.javaClass?.simpleName.orEmpty()
        val stage = if (loading) "" else when {
            screen == "NativeMainMenuScreen" && mc.level == null ->
                if (completed.contains("settings")) "returned-menu" else "main-menu"
            screen == "CreateWorldScreen" -> "create-world"
            screen.isEmpty() && mc.level != null && mc.player != null -> "world"
            screen == "NativeClickGuiScreen" -> "clickgui"
            screen == "NativeCosmeticsScreen" -> "cosmetics"
            screen == "OptionsScreen" -> "settings"
            else -> ""
        }
        val now = System.nanoTime()
        if (lastFrame == 0L) lastFrame = now
        val next = stages.firstOrNull { it !in completed } ?: return
        if (stage == next) {
            current = stage
            accumulated += now - lastFrame
        } else {
            current = stage
            accumulated = 0L
        }
        lastFrame = now
        if (accumulated < (if (stage == "world") soak else 2L) * 1_000_000_000L) return
        pending = true
        accumulated = 0L
        lastFrame = 0L
        val path = directory.resolve("$version-${filenames[stages.indexOf(stage)]}.png")
        try {
            //? if >=26 {
            /*val target = mc.gameRenderer.mainRenderTarget()
            *///?} else {
            val target = mc.mainRenderTarget
            //?}
            //? if >=1.21.5 {
            Screenshot.takeScreenshot(target) { image ->
                try {
                    save(stage, path, image.width, image.height, image.pixelsABGR) { image.writeToFile(path) }
                } finally {
                    image.close()
                }
            }
            //?} else {
            /*val image = Screenshot.takeScreenshot(target)
            try {
                //? if >=1.20 {
                val pixels = image.pixelsRGBA
                //?} else {
                val pixels = image.makePixelArray()
                //?}
                save(stage, path, image.width, image.height, pixels) { image.writeToFile(path) }
            } finally {
                image.close()
            }
            *///?}
        } catch (exception: Exception) {
            failed = true
            pending = false
            logger.error("nova-verify failure=capture stage={} run={}", stage, run, exception)
        }
    }

    private fun drive() {
        if (!auto || System.nanoTime() < driveAt) return
        if (mc.screenCompat?.javaClass?.simpleName == "NativeOobeScreen") {
            top.fpsmaster.config.ConfigManager.completeOobe()
            mc.execute { top.fpsmaster.Client.openMainMenu() }
            driveAt = System.nanoTime() + 500_000_000L
            return
        }
        //? if >=26 {
        /*
        if (prevStep != step) { prevStep = step; stepAt = System.nanoTime() }
        if (System.nanoTime() - stepAt > 10_000_000_000L && stallStep != step) {
            stallStep = step
            logger.warn("nova-verify drive stall step={} run={} screen={} overlay={} level={} player={}",
                step, run, mc.screenCompat?.javaClass?.simpleName, mc.gui.overlay()?.javaClass?.simpleName,
                mc.level != null, mc.player != null)
        }
        when (step) {
            0 -> if ("main-menu" in completed) {
                step = 1
                mc.execute { net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc) {} }
            }
            1 -> if ("create-world" in completed) {
                step = 2
                mc.execute {
                    (mc.screenCompat as? net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)?.let { cw ->
                        val buttons = cw.children().filterIsInstance<net.minecraft.client.gui.components.Button>()
                        val create = buttons.firstOrNull { it.message.string.contains("Create New World") }
                            ?: buttons.lastOrNull()
                        create?.let {
                            cw.mouseClicked(
                                net.minecraft.client.input.MouseButtonEvent(
                                    it.x + it.width / 2.0, it.y + it.height / 2.0,
                                    net.minecraft.client.input.MouseButtonInfo(0, 0)),
                                false)
                        }
                    }
                }
            }
            2 -> if ("world" in completed) {
                step = 3
                mc.execute { top.fpsmaster.Client.openClickGui() }
            }
            3 -> if ("clickgui" in completed) {
                step = 4
                mc.execute {
                    mc.screenCompat?.let { parent ->
                        mc.setScreenCompat(top.fpsmaster.ui.NativeCosmeticsScreen(parent))
                    }
                }
            }
            4 -> if ("cosmetics" in completed) {
                step = 5
                mc.execute {
                    mc.screenCompat?.let { parent ->
                        mc.setScreenCompat(net.minecraft.client.gui.screens.options.OptionsScreen(parent, mc.options, false))
                    }
                }
            }
            5 -> if ("settings" in completed) {
                step = 6
                mc.execute { mc.disconnectWithSavingScreen() }
            }
            6 -> if (mc.level == null && mc.player == null) {
                step = 7
                mc.execute { top.fpsmaster.Client.openMainMenu() }
            }
            7 -> if ("returned-menu" in completed) {
                step = 8
                mc.execute { mc.stop() }
            }
        }
        driveAt = System.nanoTime() + 500_000_000L
        *///?} else {
        when (step) {
            0 -> if ("main-menu" in completed) {
                step = 1
                //? if >=1.21.11 {
                mc.execute { net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc) {} }
                //?} else {
                /*mc.execute { net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc, mc.screenCompat) }
                *///?}
            }
            1 -> if ("create-world" in completed) {
                step = 2
                //? if >=1.21.11 {
                mc.execute {
                    (mc.screenCompat as? net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)?.let { cw ->
                        val buttons = cw.children().filterIsInstance<net.minecraft.client.gui.components.Button>()
                        val create = buttons.firstOrNull { it.message.string.contains("Create New World") }
                            ?: buttons.lastOrNull()
                        create?.let {
                            cw.mouseClicked(
                                net.minecraft.client.input.MouseButtonEvent(
                                    it.x + it.width / 2.0, it.y + it.height / 2.0,
                                    net.minecraft.client.input.MouseButtonInfo(0, 0)),
                                false)
                        }
                    }
                }
                //?} else {
                /*mc.execute {
                    (mc.screenCompat as? net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)?.let { cw ->
                        val buttons = cw.children().filterIsInstance<net.minecraft.client.gui.components.Button>()
                        val create = buttons.firstOrNull { it.message.string.contains("Create New World") }
                            ?: buttons.lastOrNull()
                        create?.let { cw.mouseClicked(it.x + it.width / 2.0, it.y + it.height / 2.0, 0) }
                    }
                }
                *///?}
            }
            2 -> if ("world" in completed) {
                step = 3
                mc.execute { top.fpsmaster.Client.openClickGui() }
            }
            3 -> if ("clickgui" in completed) {
                step = 4
                mc.execute {
                    mc.screenCompat?.let { parent ->
                        mc.setScreenCompat(top.fpsmaster.ui.NativeCosmeticsScreen(parent))
                    }
                }
            }
            4 -> if ("cosmetics" in completed) {
                step = 5
                mc.execute {
                    mc.screenCompat?.let { parent ->
                        //? if >=1.21 {
                        mc.setScreenCompat(net.minecraft.client.gui.screens.options.OptionsScreen(parent, mc.options))
                        //?} else {
                        /*mc.setScreenCompat(net.minecraft.client.gui.screens.OptionsScreen(parent, mc.options))
                        *///?}
                    }
                }
            }
            5 -> if ("settings" in completed) {
                step = 6
                //? if >=1.21.8 {
                mc.execute { mc.disconnectWithSavingScreen() }
                //?} else if >=1.21 {
                /*mc.execute { mc.disconnect() }
                *///?} else {
                /*mc.execute { mc.clearLevel() }
                *///?}
            }
            6 -> if (mc.level == null && mc.player == null) {
                step = 7
                mc.execute { top.fpsmaster.Client.openMainMenu() }
            }
            7 -> if ("returned-menu" in completed) {
                step = 8
                mc.execute { mc.stop() }
            }
        }
        driveAt = System.nanoTime() + 500_000_000L
        //?}
    }

    private fun save(stage: String, path: Path, width: Int, height: Int, pixels: IntArray, write: () -> Unit) {
        try {
            write() // Keep black/error evidence too; never turn a failed capture into a completed stage.
            val visible = pixels.count { pixel ->
                (pixel and 255) + (pixel ushr 8 and 255) + (pixel ushr 16 and 255) > 60
            }
            val colors = pixels.asSequence().map { it and 0xFFFFFF }.distinct().take(16).count()
            if (width < 320 || height < 200 || visible < pixels.size / 200 || colors < 16) {
                failed = true
                logger.error("nova-verify failure=black-or-flat-frame stage={} run={}", stage, run)
                return
            }
            completed.add(stage)
            logger.info("nova-verify stage={} run={} width={} height={} visible={} total={} soakSeconds={}",
                stage, run, width, height, visible, pixels.size, if (stage == "world") soak else 0)
        } catch (exception: Exception) {
            failed = true
            logger.error("nova-verify failure=write stage={} run={}", stage, run, exception)
        } finally {
            pending = false
        }
    }
}
