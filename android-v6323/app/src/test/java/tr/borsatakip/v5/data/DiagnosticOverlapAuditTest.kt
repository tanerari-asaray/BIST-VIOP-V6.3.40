package tr.borsatakip.v5.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticOverlapAuditTest {
    private fun event(at: Long, id: String, phase: String, code: String) =
        DiagnosticJournal.Event(at, id, "SCAN", phase, code, null, null)

    @Test fun provesOverlapOnlyFromPairedStartedAndEndedRuns() {
        val events = listOf(
            event(1000, "manual", "START", "SCAN_STARTED_MANUAL"),
            event(9000, "automatic", "START", "SCAN_STARTED_AUTOMATIC"),
            event(43000, "manual", "END", "SCAN_PARTIAL"),
            event(49000, "automatic", "END", "SCAN_PARTIAL")
        )
        val overlaps = DiagnosticOverlapAudit.overlaps(events)
        assertEquals(1, overlaps.size)
        assertEquals(34000, overlaps.single().overlapMs)
        assertEquals("manual", overlaps.single().firstRunId)
    }

    @Test fun incompleteEvidenceIsNotTreatedAsConfirmedOverlap() {
        assertTrue(DiagnosticOverlapAudit.overlaps(listOf(
            event(1000, "manual", "START", "SCAN_STARTED_MANUAL"),
            event(2000, "automatic", "START", "SCAN_STARTED_AUTOMATIC"),
            event(3000, "automatic", "END", "SCAN_PARTIAL")
        )).isEmpty())
    }
}
