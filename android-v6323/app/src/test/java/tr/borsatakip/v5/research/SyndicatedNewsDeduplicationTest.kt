package tr.borsatakip.v5.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyndicatedNewsDeduplicationTest {
    @Test
    fun syndicatedCopiesShareOneIndependenceGroup() {
        val now = 2_000_000L
        val docs = listOf(
            doc("a", "Ajans A", "Şirket yeni büyük sözleşme imzaladı", now - 1000),
            doc("b", "Site B", "Şirket yeni büyük sözleşme imzaladı", now - 900),
            doc("c", "Site C", "Şirket yeni büyük sözleşme imzaladı", now - 800)
        )
        val snapshot = ResearchFoundationEngine().build("THYAO", docs, now)
        assertEquals(1, snapshot.graph.evidence.map { it.independenceGroupId }.distinct().size)
        assertEquals(2, snapshot.graph.duplicateEvidenceIds.size)
        assertTrue(snapshot.graph.claims.single().status == VerificationStatus.PARTIALLY_VERIFIED)
    }

    private fun doc(id: String, source: String, title: String, at: Long) = ResearchInputDocument(
        documentId = id, symbol = "THYAO", category = "ŞİRKET", title = title, summary = null,
        sourceName = source, url = "https://${source.lowercase().replace(' ', '-')}.example/$id",
        publishedAt = at, receivedAt = at, availableAt = at
    )
}
