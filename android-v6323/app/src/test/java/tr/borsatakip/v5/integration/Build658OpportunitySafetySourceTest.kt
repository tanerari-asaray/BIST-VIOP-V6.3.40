package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Build658OpportunitySafetySourceTest {
    private val source = File("src/main/java/tr/borsatakip/v5/ui/OpportunityActivity.kt").readText()

    @Test fun benchmarkAbsentDoesNotShowInventedRegimeConfidence() {
        assertTrue(source.contains("marketRegime.text = \"REJİM DOĞRULANMADI\""))
        assertTrue(!source.contains("regime.confidence"))
    }

    @Test fun candidateAndInsufficientAreSeparateCategories() {
        assertTrue(source.contains("val insufficient = base.count"))
        assertTrue(source.contains("val candidates = base.count"))
        assertTrue(source.contains("VERİSİZ"))
        assertTrue(source.contains("DiscoveryFilter.WATCH -> !OpportunityTrendPresentation.from(x).verifiedSignal"))
    }
}
