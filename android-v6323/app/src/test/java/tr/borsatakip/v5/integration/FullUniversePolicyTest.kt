package tr.borsatakip.v5.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.data.FullUniversePolicy as P

class FullUniversePolicyTest {
    @Test fun symbolUniverseIsNotLimitedToThirty() {
        val many = (1..600).map { "X" + it.toString().padStart(3, '0') }
        assertEquals(600, P.normalizeSymbols(many).size)
    }
    @Test fun providerListAttemptIsNotEvidenceOfCompleteExchangeCoverage() {
        val c = P.Counters(
            discoveredBist = 600, discoveredViop = 200,
            triedBist = 600, triedViop = 200,
            successfulBist = 600, successfulViop = 200,
            bistDiscoveryOk = true, viopDiscoveryOk = true
        )
        assertTrue(c.attemptedDiscoveredUniverse)
        assertFalse(c.canClaimExchangeComplete)
        assertTrue(P.summary(c, P.State.FINISHED_UNVERIFIED).contains("TAM BORSA KAPSAMI DOĞRULANMADI"))
    }
    @Test fun emptyProviderCannotBecomeFullCoverage() {
        val c = P.Counters()
        assertFalse(c.attemptedDiscoveredUniverse)
        assertFalse(c.canClaimExchangeComplete)
    }
    @Test fun invalidCountAccountingIsRejected() {
        assertFalse(P.Counters(discoveredBist = 1, triedBist = 2).valid())
        assertFalse(P.Counters(discoveredViop = 1, triedViop = 1, successfulViop = 2).valid())
    }
    @Test fun trendRequiresTenObservedCloses() {
        assertEquals(P.Trend.UNKNOWN, P.trend(listOf(1.0, 2.0, 3.0)))
        assertEquals(P.Trend.UP, P.trend((1..10).map { it.toDouble() }))
        assertEquals(P.Trend.DOWN, P.trend((1..10).map { (11-it).toDouble() }))
        assertEquals(P.Trend.UNKNOWN, P.trend(List(10) { Double.NaN }))
    }
    @Test fun separateMarketListsNeverCreateCrossMarketPriceRanking() {
        val stock = P.Entry(P.Market.BIST, "THYAO", "Hisse", 500.0, "TRY",
            P.Trend.UNKNOWN, P.Quality.UNVERIFIED, "", 0, 0, "")
        val viop = P.Entry(P.Market.VIOP, "F_XU030", "Vadeli", 1.0, "TRY",
            P.Trend.UNKNOWN, P.Quality.UNVERIFIED, "", 0, 0, "")
        val sorted = P.visible(listOf(viop, stock), null, "")
        assertEquals(P.Market.BIST, sorted[0].market)
        assertEquals(P.Market.VIOP, sorted[1].market)
        assertEquals(listOf(viop), P.visible(listOf(viop, stock), P.Market.VIOP, ""))
    }
    @Test fun delayedPricesSortedAscendingNotCalledUndervalued() {
        fun entry(symbol: String, price: Double?) =
            P.Entry(P.Market.BIST, symbol, "", price, "TRY", P.Trend.UNKNOWN,
                P.Quality.DELAYED, "EXPERIMENTAL", 0L, 0L, "")
        val values = P.visible(
            listOf(entry("ZZZZ", null), entry("AAAA", 24.44), entry("BBBB", 0.99)), null, "")
        assertEquals(listOf("BBBB", "AAAA", "ZZZZ"), values.map { it.symbol })
        assertFalse(P.Counters().canClaimExchangeComplete)
    }
    @Test fun localFinishedIsNotFullExchangeVerified() {
        val c = P.Counters(discoveredBist=632, triedBist=632, successfulBist=629,
            noDataBist=3, bistDiscoveryOk=false)
        assertTrue(P.summary(c, P.State.LOCAL_LIST_FINISHED).contains("borsa evreni doğrulanmadı"))
        assertFalse(c.canClaimExchangeComplete)
    }
    @Test fun normalizationRemovesDuplicatesAndInvalidSymbols() {
        assertEquals(listOf("ASELS", "THYAO"), P.normalizeSymbols(
            listOf(" thyao.is ", "THYAO", "asels", "bad/value", "")
        ))
    }
}
