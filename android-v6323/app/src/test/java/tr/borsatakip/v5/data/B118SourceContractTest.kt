package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class B118SourceContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test fun mtfDailyUsesRealDailyHistoryAndNoTop12Selection() {
        val s = source("src/main/java/tr/borsatakip/v5/scan/BistScanner.kt")
        assertTrue(s.contains("provider.fetchDailyHistory(opportunity.symbol, maximumRange = false)"))
        assertTrue(s.contains("MtfConsensusEngine.Input(\"1 GÜN\", OhlcvResampler.sanitize(oneDay), 0.25)"))
        assertFalse(s.contains("take(MAX_MTF_CANDIDATES)"))
    }

    @Test fun favoritesDoNotOpenStaleOpportunityWithoutRefreshingQuote() {
        val s = source("src/main/java/tr/borsatakip/v5/ui/FavoritesActivity.kt")
        assertTrue(s.contains("val stock = prefetched ?: runCatching { resolver.fetchDisplayStockResult(symbol).stock }.getOrNull()"))
        assertFalse(s.contains("provider.fetchOne(symbol)"))
        assertTrue(s.contains("openOpportunity(existing, stock)"))
        assertTrue(s.contains("displayPrice"))
    }

    @Test fun yahooFavoriteQuoteUsesIntradayQuotePath() {
        val s = source("src/main/java/tr/borsatakip/v5/data/YahooFallbackProvider.kt")
        assertTrue(s.contains("fetchDisplayQuoteOnly(normalized)"))
        assertTrue(s.contains("range=1d&interval=1m"))
        assertTrue(s.contains("regularMarketPrice"))
        assertTrue(s.contains("regularMarketTime"))
        assertTrue(s.contains("YahooQuotePairPolicy.select(metaPrice"))
    }

    @Test fun realtimeContractRequiresSameReadinessFloor() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("minReadyBars >= CandleReadinessPolicy.requiredClosedBars()"))
        assertTrue(s.contains("V536ClockIntegrity.validateItem("))
        assertTrue(s.contains("declaredDataAgeMs = declaredDataAgeMs"))
        assertTrue(s.contains("val dataAgeMs = clockReport.measuredDataAgeMs ?: return null"))
    }

    @Test fun adaptiveWeightsUseVerifiedOpportunitiesOnly() {
        val s = source("src/main/java/tr/borsatakip/v5/data/SignalHistoryStore.kt")
        assertTrue(s.contains("decisionState\") != \"VERIFIED_OPPORTUNITY\""))
    }

    @Test fun favoriteCardRestoresSimplePreviousStructure() {
        val xml = source("src/main/res/layout/item_favorite.xml")
        assertTrue(xml.contains("@+id/favoriteDetails"))
        assertTrue(xml.contains("@+id/favoriteRemove"))
        assertFalse(xml.contains("@+id/favoriteBid"))
        assertFalse(xml.contains("@+id/favoriteAsk"))
        val scanUi = source("src/main/java/tr/borsatakip/v5/ui/ScanResultsAdapter.kt")
        val detailUi = source("src/main/java/tr/borsatakip/v5/ui/StockDetailActivity.kt")
        assertTrue(scanUi.contains("DecisionState.VERIFIED_OPPORTUNITY"))
        assertTrue(scanUi.contains("Veri geçerli • karar izleme"))
        assertTrue(detailUi.contains("VERİ BÜTÜNLÜĞÜ GEÇERLİ • KARAR İZLEME"))
    }
}
