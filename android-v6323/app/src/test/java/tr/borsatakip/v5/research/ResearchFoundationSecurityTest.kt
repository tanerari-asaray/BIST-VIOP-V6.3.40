package tr.borsatakip.v5.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchFoundationSecurityTest {
    @Test
    fun sameCanonicalSourceDoesNotCountAsIndependent() {
        val now = 10_000_000L
        val docs = listOf(
            doc("a", "Aynı Yayıncı", "Şirket yatırım kararını açıkladı", "https://same.example/a", now - 200, claimKey = "same"),
            doc("b", "Aynı Yayıncı", "Yatırım kararının ayrıntıları", "https://same.example/b", now - 100, claimKey = "same")
        )
        val snapshot = ResearchFoundationEngine().build("ASELS", docs, now)
        assertEquals(1, snapshot.graph.evidence.map { it.independenceGroupId }.distinct().size)
        assertEquals(VerificationStatus.PARTIALLY_VERIFIED, snapshot.graph.claims.single().status)
    }

    @Test
    fun originSourceIdSyndicationIsOneOrigin() {
        val now = 11_000_000L
        val docs = listOf(
            doc("a", "Site A", "Şirket sözleşme imzaladı", "https://a.example/a", now - 200, claimKey = "contract", originSourceId = "agency:42"),
            doc("b", "Site B", "Sözleşme ayrıntıları duyuruldu", "https://b.example/b", now - 100, claimKey = "contract", originSourceId = "agency:42")
        )
        val snapshot = ResearchFoundationEngine().build("ASELS", docs, now)
        assertEquals(1, snapshot.graph.evidence.map { it.independenceGroupId }.distinct().size)
        assertEquals(1, snapshot.graph.duplicateEvidenceIds.size)
        assertEquals(VerificationStatus.PARTIALLY_VERIFIED, snapshot.graph.claims.single().status)
    }

    @Test
    fun fakeKapHostCannotBecomeAuthoritative() {
        val now = 12_000_000L
        val fake = doc("fake", "KAP", "Yeni sözleşme", "https://untrusted.example/fake", now - 100,
            sourceType = SourceType.KAP, providerVerified = true)
        val snapshot = ResearchFoundationEngine().build("ASELS", listOf(fake), now)
        val evidence = snapshot.graph.evidence.single()
        assertFalse(evidence.sourceType == SourceType.KAP)
        assertFalse(evidence.sourceIdentityVerified)
        assertEquals(VerificationStatus.PARTIALLY_VERIFIED, snapshot.graph.claims.single().status)
    }

    @Test
    fun kapCategoryDoesNotCreateKapAuthority() {
        val now = 13_000_000L
        val fake = ResearchInputDocument(
            documentId = "fake", symbol = "ASELS", category = "KAP", title = "Yeni sözleşme", summary = null,
            sourceName = "Bir Haber Sitesi", url = "https://news.example/rss1", publishedAt = now - 100,
            receivedAt = now - 50, availableAt = now - 50, providerVerified = true
        )
        val snapshot = ResearchFoundationEngine().build("ASELS", listOf(fake), now)
        assertEquals(SourceType.NEWS_MEDIA, snapshot.graph.evidence.single().sourceType)
        assertEquals(VerificationStatus.PARTIALLY_VERIFIED, snapshot.graph.claims.single().status)
    }

    @Test
    fun providerVerificationIsRequiredForKapAuthority() {
        val now = 14_000_000L
        val unverified = doc("kap", "KAP", "Yeni sözleşme", "https://kap.org.tr/tr/Bildirim/1", now - 100,
            sourceType = SourceType.KAP, providerVerified = false)
        val snapshot = ResearchFoundationEngine().build("ASELS", listOf(unverified), now)
        assertEquals(SourceType.KAP, snapshot.graph.evidence.single().sourceType)
        assertFalse(snapshot.graph.evidence.single().sourceIdentityVerified)
        assertEquals(VerificationStatus.PARTIALLY_VERIFIED, snapshot.graph.claims.single().status)
    }

    @Test
    fun differentHeadlinesCanResolveToSameClaim() {
        val now = 15_000_000L
        val docs = listOf(
            doc("a", "Kaynak A", "Şirket yeni yatırım kararı açıkladı", "https://a.example/x", now - 200),
            doc("b", "Kaynak B", "Yeni tesis yatırımı için karar alındı", "https://b.example/y", now - 100)
        )
        val snapshot = ResearchFoundationEngine().build("ASELS", docs, now)
        assertEquals(1, snapshot.graph.claims.size)
        assertEquals(VerificationStatus.VERIFIED, snapshot.graph.claims.single().status)
    }

    @Test
    fun oneEventCanCarryMultipleClaims() {
        val now = 16_000_000L
        val docs = listOf(
            doc("a", "Kaynak A", "Yatırım kararı açıklandı", "https://a.example/x", now - 200, claimKey = "claim-a", eventKey = "event-x"),
            doc("b", "Kaynak B", "Yatırım finansmanı açıklandı", "https://b.example/y", now - 100, claimKey = "claim-b", eventKey = "event-x")
        )
        val snapshot = ResearchFoundationEngine().build("ASELS", docs, now)
        assertEquals(2, snapshot.graph.claims.size)
        assertEquals(1, snapshot.graph.events.size)
        assertEquals(2, snapshot.graph.events.single().relatedClaimIds.size)
    }

    @Test
    fun futureEventDateKnownNowIsNotRejected() {
        val now = 17_000_000L
        val futureEvent = doc("future-event", "Kaynak", "Gelecek ay yatırım başlayacak", "https://news.example/a", now - 100,
            eventTime = now + 30L * 24 * 60 * 60 * 1000)
        val snapshot = ResearchFoundationEngine().build("ASELS", listOf(futureEvent), now)
        assertEquals(1, snapshot.graph.evidence.size)
        assertTrue(snapshot.graph.rejectedEvidenceIds.isEmpty())
    }

    @Test
    fun temporalFilterRunsBeforeDeduplication() {
        val now = 18_000_000L
        val past = doc("past", "Haber", "Yeni yatırım açıklandı", "https://news.example/past", now - 100)
        val future = doc("future", "KAP", "Yeni yatırım açıklandı", "https://kap.org.tr/tr/Bildirim/99", now + 1000,
            sourceType = SourceType.KAP, providerVerified = true)
        val snapshot = ResearchFoundationEngine().build("ASELS", listOf(past, future), now)
        assertEquals(listOf("future"), snapshot.graph.rejectedEvidenceIds)
        assertTrue(snapshot.graph.duplicateEvidenceIds.isEmpty())
        assertEquals(1, snapshot.graph.evidence.size)
    }

    private fun doc(
        id: String,
        source: String,
        title: String,
        url: String,
        published: Long,
        claimKey: String? = null,
        eventKey: String? = null,
        originSourceId: String? = null,
        sourceType: SourceType = SourceType.UNKNOWN,
        providerVerified: Boolean = false,
        eventTime: Long? = null
    ) = ResearchInputDocument(
        documentId = id,
        symbol = "ASELS",
        category = "ŞİRKET",
        title = title,
        summary = null,
        sourceName = source,
        url = url,
        publishedAt = published,
        eventTime = eventTime,
        receivedAt = published,
        availableAt = published,
        sourceType = sourceType,
        providerVerified = providerVerified,
        claimKey = claimKey,
        eventKey = eventKey,
        originSourceId = originSourceId
    )
}
