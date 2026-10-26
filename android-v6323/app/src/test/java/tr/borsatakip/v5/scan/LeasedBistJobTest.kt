package tr.borsatakip.v5.scan

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.After

@OptIn(ExperimentalCoroutinesApi::class)
class LeasedBistJobTest {
    @After fun releaseRemainingLease() { BistScanGate.snapshot()?.let { BistScanGate.release(it) } }
    @Test fun cancellationBeforeDispatchReleasesLeaseWithoutEnteringBody() = runTest {
        val lease = requireNotNull(BistScanGate.tryAcquire("before-start", "TEST"))
        var entered = false
        val job = launchWithBistLease(lease) { entered = true }
        job.cancel()
        runCurrent()
        assertFalse(entered)
        assertNull(BistScanGate.snapshot())
    }

    @Test fun cancelledParentStillReleasesAcquiredLease() = runTest {
        val parent = Job().apply { cancel() }
        val scope = CoroutineScope(parent + StandardTestDispatcher(testScheduler))
        val lease = requireNotNull(BistScanGate.tryAcquire("cancelled-parent", "TEST"))
        val job = scope.launchWithBistLease(lease) { error("Body must not run") }
        job.join()
        assertNull(BistScanGate.snapshot())
    }

    @Test fun cancellationAfterStartWaitsForCleanupThenReleases() = runTest {
        val lease = requireNotNull(BistScanGate.tryAcquire("running", "TEST"))
        var cleaned = false
        val job = launchWithBistLease(lease) {
            try { awaitCancellation() } finally { cleaned = true }
        }
        runCurrent()
        assertNotNull(BistScanGate.snapshot())
        job.cancelAndJoin()
        assertTrue(cleaned)
        assertNull(BistScanGate.snapshot())
    }

    @Test fun setupExceptionReleasesLease() = runTest {
        var failure: Throwable? = null
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, e -> failure = e })
        val lease = requireNotNull(BistScanGate.tryAcquire("setup-failure", "TEST"))
        val job = scope.launchWithBistLease(lease) { throw IllegalStateException("setup") }
        job.join()
        assertTrue(failure is IllegalStateException)
        assertNull(BistScanGate.snapshot())
        scope.cancel()
    }

    @Test fun normalCompletionReleasesLeaseForNextScan() = runTest {
        val lease = requireNotNull(BistScanGate.tryAcquire("normal", "TEST"))
        launchWithBistLease(lease) { }.join()
        val next = requireNotNull(BistScanGate.tryAcquire("next", "TEST"))
        assertTrue(BistScanGate.release(next))
    }
}
