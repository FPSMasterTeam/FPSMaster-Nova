package top.fpsmaster.diagnostics

import top.fpsmaster.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/** Opt-in render smoke evidence. The JIT folds every guarded call away when the property is unset. */
object Smoke {
    @JvmField
    val ENABLED: Boolean = java.lang.Boolean.getBoolean("fpsmaster.smoke")

    private val mixins = ConcurrentHashMap.newKeySet<String>()
    private val features = ConcurrentHashMap.newKeySet<String>()
    private val allocated = LongAdder()
    private val released = LongAdder()

    @JvmStatic
    fun mixin(id: String) {
        if (ENABLED && mixins.add(id)) logger.info("fpsmaster-smoke mixin={}", id)
    }

    @JvmStatic
    fun feature(id: String) {
        if (ENABLED && features.add(id)) logger.info("fpsmaster-smoke feature={}", id)
    }

    @JvmStatic
    fun allocated(id: String) {
        if (!ENABLED) return
        allocated.increment()
        logger.info("fpsmaster-smoke resource={} net={}", id, allocated.sum() - released.sum())
    }

    @JvmStatic
    fun released(id: String) {
        if (!ENABLED) return
        released.increment()
        logger.info("fpsmaster-smoke resource={} net={}", id, allocated.sum() - released.sum())
    }
}
