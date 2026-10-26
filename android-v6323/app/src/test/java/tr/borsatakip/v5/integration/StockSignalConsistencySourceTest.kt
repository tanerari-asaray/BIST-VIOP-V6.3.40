package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevent the Stocks tabs from treating directional observations as validated trade signals. */
class StockSignalConsistencySourceTest {
    private val stocks = File("src/main/java/tr/borsatakip/v5/ui/StocksActivity.kt").readText()
    private val home = File("src/main/java/tr/borsatakip/v5/ui/MainActivity.kt").readText()

    @Test fun longAndShortRequireTheSameValidatedSignalGateAsHome() {
        assertTrue(stocks.contains("OpportunityTrendPresentation.from(item).verifiedSignal && item.direction.equals(\"LONG\""))
        assertTrue(stocks.contains("OpportunityTrendPresentation.from(item).verifiedSignal && item.direction.equals(\"SHORT\""))
        assertTrue(home.contains("OpportunityTrendPresentation.from(it).verifiedSignal"))
    }

    @Test fun missingScanAndUnverifiedSignalsDoNotClaimNoOpportunities() {
        assertTrue(home.contains("TARAMA SONUCU YOK"))
        assertTrue(home.contains("CANLI SİNYAL DOĞRULANAMADI"))
        assertTrue(stocks.contains("TARAMA SONUCU YOK"))
    }
}
