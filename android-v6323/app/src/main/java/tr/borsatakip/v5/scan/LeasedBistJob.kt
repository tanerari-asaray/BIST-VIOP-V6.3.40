package tr.borsatakip.v5.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Completion handlers also run when DEFAULT launch is cancelled before its body starts. */
internal fun CoroutineScope.launchWithBistLease(
    lease: BistScanGate.Lease, block: suspend CoroutineScope.() -> Unit
): Job = try {
    launch(block = block).also { job -> job.invokeOnCompletion { BistScanGate.release(lease) } }
} catch (failure: Throwable) {
    BistScanGate.release(lease)
    throw failure
}
