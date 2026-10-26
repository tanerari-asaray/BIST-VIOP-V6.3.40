package tr.borsatakip.v5.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointInTimeIsolationTest {
    @Test
    fun futureOrNotYetAvailableEvidenceIsExcluded() {
        val asOf = 1_000_000L
        val docs = listOf(
            doc("ok", 900_000L, 950_000L),
            doc("future-publish", 1_100_000L, 900_000L),
            doc("future-available", 800_000L, 1_100_000L)
        )
        val snapshot = ResearchFoundationEngine().build("ASELS", docs, asOf)
        assertEquals(1, snapshot.graph.evidence.size)
        assertEquals(setOf("future-available", "future-publish"), snapshot.graph.rejectedEvidenceIds.toSet())
        assertTrue(snapshot.status == ResearchStatus.PARTIAL)
    }

    private fun doc(id: String, published: Long, available: Long) = ResearchInputDocument(
        documentId = id, symbol = "ASELS", category = "KAP", title = "Yeni sözleşme", summary = null,
        sourceName = "KAP", url = "https://kap.org.tr/$id", publishedAt = published,
        receivedAt = available, availableAt = available, sourceType = SourceType.KAP
    )
}
