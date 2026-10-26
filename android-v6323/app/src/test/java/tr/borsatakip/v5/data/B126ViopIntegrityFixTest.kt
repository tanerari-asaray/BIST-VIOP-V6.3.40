package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.analysis.DecisionPricePolicy
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.ViopContract

class B126ViopIntegrityFixTest {
    private fun c(symbol: String, validity: SignalValidity, expiryAt: Long, volume: Double = 1.0) = ViopContract(
        symbol = symbol, underlying = "BIMAS", expiry = "2026-12", tickSize = 0.01, multiplier = 1.0,
        validity = validity, expiryAt = expiryAt, lastTradingAt = expiryAt, volume = volume
    )

    @Test fun validContractBeatsWatchEvenWhenWatchAppearsFirst() {
        val now = 1_000L
        val watch = c("WATCH", SignalValidity.WATCH, 2_000L, 999.0)
        val valid = c("VALID", SignalValidity.VALID, 3_000L, 1.0)
        assertEquals("VALID", ViopContractSelector.candidates(listOf(watch, valid), nowMs = now).first().symbol)
    }

    @Test fun rejectedAndExpiredContractsAreNotSelected() {
        val now = 5_000L
        val rejected = c("REJECTED", SignalValidity.REJECTED, 9_000L)
        val expired = c("EXPIRED", SignalValidity.VALID, 4_000L)
        assertTrue(ViopContractSelector.candidates(listOf(rejected, expired), nowMs = now).isEmpty())
    }

    @Test fun underlyingResolverReturnsRealBackendContractOnly() {
        val now = 1_000L
        val bimas = c("F_REAL_BIMAS", SignalValidity.VALID, 2_000L)
        val other = bimas.copy(symbol = "F_OTHER", underlying = "THYAO")
        assertEquals("F_REAL_BIMAS", ViopContractSelector.resolve("BIMAS", listOf(other, bimas), now)!!.symbol)
    }

    @Test fun decisionPriceDoesNotFallBackToLiveQuote() {
        assertNull(DecisionPricePolicy.historyBarPrice(emptyList(), 123.45))
        val candle = Candle(1L, 10.0, 11.0, 9.0, 10.5, 100.0)
        assertEquals(10.5, DecisionPricePolicy.historyBarPrice(listOf(candle), 123.45)!!, 0.0001)
    }

    @Test fun sourceContainsStrictHistoryContractAndNoSilentRepair() {
        val s = java.io.File("src/main/java/tr/borsatakip/v5/data/BackendProvider.kt").readText()
        assertTrue(s.contains("lastBarClosed"))
        assertTrue(s.contains("ViopHistoryIntegrityPolicy.validateMetadata"))
        assertTrue(s.contains("ViopHistoryIntegrityPolicy.validateCandles"))
        assertTrue(s.contains("ViopHistoryIntegrityPolicy.validateLastBarClosed"))
        assertFalse(s.contains(".distinctBy{it.timestamp}.sortedBy{it.timestamp}"))
    }

    @Test fun dashboardKeepsQuoteButMarksMissingHistoryAsInsufficientAnalysis() {
        val s = java.io.File("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt").readText()
        assertTrue(s.contains("backend.loadViopQuote(c.symbol).getOrThrow()"))
        assertTrue(s.contains("loadViopHistoryFlexible(c.symbol, \"1y\", \"1d\", 0)"))
        assertTrue(s.contains("h.size < ViopScanner.MIN_HISTORY_BARS"))
        assertTrue(s.contains("ANALİZ YETERSİZ"))
        val detail = java.io.File("src/main/java/tr/borsatakip/v5/ui/ViopDetailActivity.kt").readText()
        assertTrue(detail.contains("Quote ve strict history doğrulanmadan gerçek VİOP LONG/SHORT sinyali üretilmez."))
    }

    @Test fun preflightNoLongerUsesContractsIndexZero() {
        val s = java.io.File("src/main/java/tr/borsatakip/v5/data/BackendPreflightClient.kt").readText()
        assertFalse(s.contains("optJSONObject(0)"))
        assertTrue(s.contains("ViopContractSelector.candidates"))
        assertTrue(s.contains("MAX_VIOP_PREFLIGHT_CANDIDATES"))
    }
}
