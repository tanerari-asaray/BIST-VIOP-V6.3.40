package tr.borsatakip.v5.worker

import org.junit.Assert.*
import org.junit.Test

class AutoScanCompletionTest {
    @Test fun onlyTheCompletedWorkerOutcomeIsFullyVerified() {
        AutomaticScanRunner.Outcome.values().forEach { outcome ->
            assertEquals(outcome.name, outcome == AutomaticScanRunner.Outcome.COMPLETED,
                AutoScanUiPolicy.isCompleted(outcome.name))
        }
    }
    @Test fun legacyAndUnknownLabelsDoNotClaimCompletion() {
        listOf("COMPLETE", "", "TESTING", "completed").forEach { assertFalse(AutoScanUiPolicy.isCompleted(it)) }
    }
}
