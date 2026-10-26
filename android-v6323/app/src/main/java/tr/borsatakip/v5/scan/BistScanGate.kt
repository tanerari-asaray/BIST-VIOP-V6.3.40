package tr.borsatakip.v5.scan

import java.util.concurrent.atomic.AtomicReference

/**
 * One in-process BIST scanner at a time, across UI/foreground service/WorkManager.
 * A rejected request is a SKIP, never an upstream failure or a tradable result.
 * A lease is released in BistScanner.scan's finally, including cancellation.
 * Separate OS processes/devices still require a server-side lease if enabled.
 */
object BistScanGate {
    class Lease internal constructor(
        val runId: String,
        val owner: String,
        private val startedNanos: Long
    ) {
        val elapsedMs: Long get() = ((System.nanoTime() - startedNanos) / 1_000_000L).coerceAtLeast(0L)
    }

    private val active = AtomicReference<Lease?>(null)

    fun tryAcquire(runId: String, owner: String): Lease? {
        val lease = Lease(runId, owner, System.nanoTime())
        return if (active.compareAndSet(null, lease)) lease else null
    }

    fun snapshot(): Lease? = active.get()

    fun release(lease: Lease): Boolean = active.compareAndSet(lease, null)
}
