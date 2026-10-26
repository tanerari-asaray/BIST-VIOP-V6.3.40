package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Build659OpportunityDesignSourceTest {
    private val source = File("src/main/java/tr/borsatakip/v5/ui/OpportunityActivity.kt").readText()
    private val layout = File("src/main/res/layout/activity_opportunity.xml").readText()

    @Test fun visibleScannerControlsAreConnected() {
        assertTrue(layout.contains("btnVerifiedOnly"))
        assertTrue(layout.contains("btnCandidatesOnly"))
        assertTrue(layout.contains("btnLowPriceSort"))
        assertTrue(source.contains("R.id.btnVerifiedOnly"))
        assertTrue(source.contains("R.id.btnCandidatesOnly"))
        assertTrue(source.contains("DiscoverySort.CHEAPEST -> eligible.sortedBy"))
    }

    @Test fun unverifiableDataIsNotPromotedToLiveTrade() {
        assertTrue(source.contains("OpportunityTrendPresentation.from(it).verifiedSignal"))
        assertTrue(source.contains("REJİM DOĞRULANMADI"))
        assertTrue(layout.contains("txtScanModeDisclosure"))
    }
}
