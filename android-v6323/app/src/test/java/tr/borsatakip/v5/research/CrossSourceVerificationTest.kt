package tr.borsatakip.v5.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSourceVerificationTest {
    @Test
    fun twoIndependentSourcesVerifyOneClaim() {
        val now = 3_000_000L
        val docs = listOf(
            ResearchInputDocument("a", "EREGL", "ŞİRKET", "Şirket yeni yatırım kararı açıkladı", null, "Şirket Yatırımcı İlişkileri", "https://company.example/a", now - 20, receivedAt = now - 10, availableAt = now - 10, sourceType = SourceType.COMPANY, claimKey = "investment-2026-09"),
            ResearchInputDocument("b", "EREGL", "ŞİRKET", "Yeni tesis yatırımı için karar alındı", null, "Bağımsız Haber", "https://news.example/b", now - 10, receivedAt = now - 5, availableAt = now - 5, sourceType = SourceType.NEWS_MEDIA, claimKey = "investment-2026-09")
        )
        val snapshot = ResearchFoundationEngine().build("EREGL", docs, now)
        assertEquals(1, snapshot.graph.claims.size)
        assertEquals(VerificationStatus.VERIFIED, snapshot.graph.claims.single().status)
        assertTrue(snapshot.confidence.claimConfidence > 0.0)
    }

    @Test
    fun authoritativeKapSourceCanVerifyWithoutCopyCountInflation() {
        val now = 4_000_000L
        val doc = ResearchInputDocument("kap1", "ASELS", "KAP", "Yeni sözleşme imzalandı", null, "KAP", "https://kap.org.tr/kap1", now - 100, receivedAt = now - 50, availableAt = now - 50, sourceType = SourceType.KAP, providerVerified = true)
        val snapshot = ResearchFoundationEngine().build("ASELS", listOf(doc), now)
        assertEquals(VerificationStatus.VERIFIED, snapshot.graph.claims.single().status)
    }
}
