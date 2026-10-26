package tr.borsatakip.v5.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimEvidenceMappingTest {
    @Test
    fun everyEvidencePointsToExistingClaimAndEvent() {
        val now = 5_000_000L
        val doc = ResearchInputDocument("1", "TUPRS", "ŞİRKET", "Yeni yatırım açıklandı", "Tesis yatırımı", "Kaynak", "https://example.com/1", now - 100, receivedAt = now - 50, availableAt = now - 50)
        val snapshot = ResearchFoundationEngine().build("TUPRS", listOf(doc), now)
        val claimIds = snapshot.graph.claims.map { it.claimId }.toSet()
        assertTrue(snapshot.graph.evidence.all { row -> row.claimIds.all { it in claimIds } })
        assertEquals(snapshot.graph.claims.single().eventId, snapshot.graph.events.single().eventId)
    }
}
