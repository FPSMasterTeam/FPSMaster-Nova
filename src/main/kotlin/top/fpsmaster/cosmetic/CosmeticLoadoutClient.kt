package top.fpsmaster.cosmetic

import io.github.vlouboos.standaloneevent.api.EventHandler
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI
import top.fpsmaster.auth.AuthService
import top.fpsmaster.auth.CosmeticLoadoutRequest
import top.fpsmaster.auth.CosmeticLoadoutView
import top.fpsmaster.auth.FPSMasterApiClient
import top.fpsmaster.auth.MinecraftLinkClient
import top.fpsmaster.event.client.TickEvent
import top.fpsmaster.logger
import top.fpsmaster.mc
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps this account's equipped cosmetics on the server so the same loadout appears on every client
 * the account signs into, and so other players can resolve it.
 *
 * Writes are immediate for discrete actions (equip, cape animation, wings on/off) and debounced for
 * the size slider, which otherwise produces a request per frame while dragging. A pending write is
 * flushed when the cosmetics screen closes. A failed write never pretends to have synced: the local
 * loadout keeps rendering and [status] reports `failed`, which the cosmetics screen surfaces.
 */
object CosmeticLoadoutClient {
    private const val SLIDER_DEBOUNCE_MS = 500L

    enum class Status {
        /** Server copy matches the local one (or there is nothing to sync yet). */
        OK,

        /** Write in flight. */
        SYNCING,

        /** The backend has no loadout endpoint: this build talks to an older server. */
        UNAVAILABLE,

        /** Last write or read failed. Local cosmetics still render. */
        FAILED
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "FPSMaster-Loadout").apply { isDaemon = true }
    }

    @Volatile
    var status: Status = Status.OK
        private set

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    private var pendingPush: ScheduledFuture<*>? = null

    @Volatile
    private var applying = false

    @Volatile
    private var lastView: CosmeticLoadoutView? = null

    @Volatile
    private var lastPushed: CosmeticLoadoutRequest? = null

    @Volatile
    private var linkAttempted = false

    fun initialize() {
        StandaloneEventAPI.getApi().register(this)
        pull()
    }

    /** Pull the account loadout and apply it locally. Called on client start and after sign-in. */
    fun pull() {
        if (!AuthService.isLoggedIn()) {
            status = Status.OK
            return
        }
        status = Status.SYNCING
        FPSMasterApiClient.getCosmeticLoadout().whenComplete { result, exception ->
            when {
                exception != null -> failed(exception.message ?: exception.javaClass.simpleName)
                result.statusCode == 404 -> unavailable()
                result.success -> {
                    lastView = result.data
                    result.data?.let(::apply)
                    status = Status.OK
                    lastError = ""
                }

                else -> failed(result.message)
            }
        }
    }

    /** Re-apply the last pulled loadout once the owned-item list (and its scale policy) is known. */
    fun onOwnedRefreshed() {
        lastView?.let(::apply)
    }

    /** Discrete change (equip, toggle): write straight away. */
    fun pushNow() {
        if (applying) return
        cancelPending()
        push()
    }

    /** Slider change: coalesce into one write [SLIDER_DEBOUNCE_MS] after the last movement. */
    fun pushDebounced() {
        if (applying) return
        cancelPending()
        pendingPush = scheduler.schedule({ push() }, SLIDER_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    /** Send a pending debounced write immediately (cosmetics screen close, client shutdown). */
    fun flush() {
        if (pendingPush == null) return
        cancelPending()
        push()
    }

    fun clear() {
        cancelPending()
        lastView = null
        lastPushed = null
        linkAttempted = false
        status = Status.OK
        lastError = ""
    }

    /** `ok` / `unavailable` / `failed`, as the shared cosmetics screen expects. */
    fun statusId(): String = when (status) {
        Status.UNAVAILABLE -> "unavailable"
        Status.FAILED -> "failed"
        else -> "ok"
    }

    @EventHandler
    @Suppress("unused")
    fun onTick(@Suppress("UNUSED_PARAMETER") event: TickEvent) {
        // Proving the Minecraft account needs a live session, so it waits for a world rather than
        // running at start-up. One attempt per session; failures do not retry in a loop.
        if (linkAttempted || mc.player == null || !AuthService.isLoggedIn()) {
            return
        }
        linkAttempted = true
        MinecraftLinkClient.ensureLinked()
    }

    private fun push() {
        if (!AuthService.isLoggedIn() || status == Status.UNAVAILABLE) {
            return
        }
        val request = snapshot()
        if (request == lastPushed) {
            return
        }

        status = Status.SYNCING
        FPSMasterApiClient.putCosmeticLoadout(request).whenComplete { result, exception ->
            when {
                exception != null -> failed(exception.message ?: exception.javaClass.simpleName)
                result.statusCode == 404 -> unavailable()
                result.success -> {
                    lastPushed = request
                    lastView = result.data
                    status = Status.OK
                    lastError = ""
                }

                else -> failed(result.message)
            }
        }
    }

    private fun snapshot(): CosmeticLoadoutRequest {
        val backId = CosmeticManager.selectedWingsId
        val builtin = backId == CosmeticManager.BUILTIN_WINGS_ID
        val wearing = CosmeticManager.wingsEnabled
        return CosmeticLoadoutRequest(
            // Locally authored cosmetics have no backend id and stay on this machine.
            capeItemId = CosmeticManager.selectedCapeId?.toLongOrNull(),
            backItemId = if (wearing && !builtin) backId.toLongOrNull() else null,
            builtinWingsEnabled = wearing && builtin,
            wingScale = CosmeticManager.savedWingScale,
            capeAnimationEnabled = CosmeticManager.capeAnimationEnabled
        )
    }

    private fun apply(view: CosmeticLoadoutView) {
        applying = true
        try {
            val backId = view.backItemId?.toString()
            CosmeticManager.configure(
                capeId = view.capeItemId?.toString(),
                wingsId = backId ?: CosmeticManager.BUILTIN_WINGS_ID,
                wingsEnabled = backId != null || view.builtinWingsEnabled,
                capeAnimationEnabled = view.capeAnimationEnabled,
                wingScale = view.wingScaleValue()
            )
            lastPushed = snapshot()
        } finally {
            applying = false
        }
    }

    private fun cancelPending() {
        pendingPush?.cancel(false)
        pendingPush = null
    }

    private fun unavailable() {
        status = Status.UNAVAILABLE
        lastError = "loadout endpoint not available"
        logger.warn("Cosmetic loadout sync unavailable: backend has no /me/cosmetics/loadout endpoint")
    }

    private fun failed(message: String) {
        status = Status.FAILED
        lastError = message
        logger.warn("Cosmetic loadout sync failed: {}", message)
    }
}
