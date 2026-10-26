package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class DeepDiagnosticEngineTest {
    private fun issue(id: String, status: DeepDiagnosticEngine.Status, at: Long = 100L): DeepDiagnosticEngine.Check =
        DeepDiagnosticEngine.Check(id, "BIST", "MISSING_PRICE", status,
            DeepDiagnosticEngine.Priority.P2, DeepDiagnosticEngine.Proof.RECORDED_RUN, at,
            "Fiyat yok", "Boş", "Fiyat", "Analiz eksik", "Veri kaynağı olabilir", null,
            "Kaynağı doğrula", "Gerçek istekle tekrar test et", "DATA_SOURCE")
    @Test fun oneHundredDistinctFailuresAreNeverSilentlyDropped() {
        val report = DeepDiagnosticEngine.evaluate((1..100).map { issue("SYM-$it", DeepDiagnosticEngine.Status.NO_DATA) })
        assertEquals(100, report.summary.total)
        assertEquals(100, report.summary.noData)
        assertEquals(100, report.groups["DATA_SOURCE"]!!.size)
        assertTrue(report.text().contains("100. [P2]"))
        assertTrue(report.text().contains("SYM-100"))
    }
    @Test fun duplicateIssueIdsAreRejected() {
        try {
            DeepDiagnosticEngine.evaluate(listOf(issue("A", DeepDiagnosticEngine.Status.FAIL), issue("A", DeepDiagnosticEngine.Status.FAIL)))
            fail("Expected duplicate rejection")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun missingAndUntestedNeverBecomeSuccess() {
        val r = DeepDiagnosticEngine.evaluate(listOf(issue("A", DeepDiagnosticEngine.Status.NOT_TESTED), issue("B", DeepDiagnosticEngine.Status.UNVERIFIED)))
        assertEquals(0, r.summary.passed)
        assertEquals(1, r.summary.notTested)
        assertEquals(1, r.summary.unverified)
    }
    @Test fun disappearanceDoesNotCloseIssue() {
        val delta = DeepDiagnosticEngine.compare(mapOf("A" to 100L), emptyList(), 100L)
        assertEquals(listOf("A"), delta.notReverified)
        assertTrue(delta.resolved.isEmpty())
    }
    @Test fun onlyFreshRecordedPassingRetestClosesIssue() {
        val first = DeepDiagnosticEngine.compare(mapOf("A" to 100L), listOf(issue("A", DeepDiagnosticEngine.Status.PASS, 100L)), 100L)
        assertTrue(first.resolved.isEmpty())
        val second = DeepDiagnosticEngine.compare(mapOf("A" to 100L), listOf(issue("A", DeepDiagnosticEngine.Status.PASS, 200L)), 100L)
        assertEquals(listOf("A"), second.resolved)
    }
    @Test fun safetyGateIsNotCountedAsFailure() {
        val r = DeepDiagnosticEngine.evaluate(listOf(issue("SAFE", DeepDiagnosticEngine.Status.SAFETY_BLOCK)))
        assertEquals(0, r.summary.failures)
        assertEquals(1, r.summary.safetyBlocks)
    }
}
