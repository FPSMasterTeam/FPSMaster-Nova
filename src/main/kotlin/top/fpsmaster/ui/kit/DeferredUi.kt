package top.fpsmaster.ui.kit

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Prism 是立即模式 UI：按钮回调在 `Screen.render` 里派发，而不是在 `mouseClicked` 里。
 * 如果回调直接 `setScreen`，而新屏幕的 `init` 内部又走 `Minecraft.forceSetScreen`
 * （1.19.2 的 `CreateWorldScreen.openFresh` 就是：`queueLoadScreen` → `forceSetScreen`
 * → `runTick(false)`），vanilla 会在 render 调用栈里再嵌套跑一次 `GameRenderer.render`。
 *
 * fabric-screen-api-v1 的 `GameRendererMixin` 用一个共享的 `@Unique renderingScreen`
 * 字段配对 before/after 事件，afterRender 末尾把它置回 null。嵌套那一次渲染会先把字段
 * 清掉，等外层 afterRender 再读就是 null，直接
 * `NullPointerException: Screen cannot be null`（崩溃报告里 `Screen name` 反而能正常
 * 解析，因为 `Minecraft.screen` 自始至终非 null——空的不是它）。
 *
 * 所以 paint 期间发起的切屏一律排队，等到下一次 `Minecraft.runTick` 头部
 * （[Client.tick] → [drain]）再执行，那时已经出了 render 调用栈。
 */
object DeferredUi {
    private val pending = ConcurrentLinkedQueue<Runnable>()

    @Volatile
    private var painting = false

    /** 标记 [body] 处于 UI paint 期间；期间的切屏请求会被推迟。 */
    fun <T> whilePainting(body: () -> T): T {
        val previous = painting
        painting = true
        return try {
            body()
        } finally {
            painting = previous
        }
    }

    fun painting(): Boolean = painting

    fun defer(task: Runnable) {
        pending.add(task)
    }

    /** 在 `runTick` 头部调用：此处不在任何 `GameRenderer.render` 栈里。 */
    fun drain() {
        while (true) {
            (pending.poll() ?: return).run()
        }
    }
}
