package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Avoid calling all scanned rows verified actionable trading opportunities. */
class OpportunityTruthSourceTest {
    private val screen = File("src/main/java/tr/borsatakip/v5/ui/OpportunityActivity.kt").readText()

    @Test fun opportunityCounterCountsOnlyVerifiedSignals() {
        assertTrue(screen.contains("items.count { OpportunityTrendPresentation.from(it).verifiedSignal }"))
    }

    @Test fun strongBuySellExcludeUnverifiedCandidates() {
        assertTrue(screen.contains("DiscoveryFilter.STRONG_BUY -> OpportunityTrendPresentation.from(x).verifiedSignal"))
        assertTrue(screen.contains("DiscoveryFilter.STRONG_SELL -> OpportunityTrendPresentation.from(x).verifiedSignal"))
    }

    @Test fun missingBenchmarkCannotBecomeVerifiedRegime() {
        assertTrue(screen.contains("REJİM DOĞRULANMADI"))
        assertTrue(screen.contains("Tüm Analizler"))
    }
}
