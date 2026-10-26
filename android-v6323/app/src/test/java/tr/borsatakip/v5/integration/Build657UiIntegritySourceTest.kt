package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Build657UiIntegritySourceTest {
    private val opportunities = File("src/main/java/tr/borsatakip/v5/ui/OpportunityActivity.kt").readText()
    private val stocks = File("src/main/java/tr/borsatakip/v5/ui/StocksActivity.kt").readText()

    @Test fun separatelyCountsVerifiedCandidateAndRejectedRecords() {
        assertTrue(opportunities.contains("val verifiedCount = base.count"))
        assertTrue(opportunities.contains("val candidates = base.count"))
        assertTrue(opportunities.contains("val rejected = base.count"))
        assertTrue(opportunities.contains("ONAYLI"))
        assertTrue(opportunities.contains("ADAY"))
    }

    @Test fun unavailableIndexMembershipAndQuotesAreNeverGuessed() {
        assertTrue(stocks.contains("BIST 30 üyelik verisi doğrulanmadı"))
        assertTrue(stocks.contains("Endeks verisi yok"))
    }
}
