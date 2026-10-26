package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.RankingStatus
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.TechnicalSnapshot

class OpportunityTrendPresentationTest {
    private val now = 1_800_000_000_000L

    private fun technical(bullish: Boolean, sufficient: Boolean = true) = TechnicalSnapshot(
        ema20 = if (!sufficient) null else if (bullish) 110.0 else 90.0,
        ema50 = 100.0, ema200 = 95.0, rsi14 = 50.0,
        macd = 1.0, macdSignal = 0.5, bbUpper = null, bbLower = null,
        atr14 = null, vwap = null, volumeRatio = null, support = null, resistance = null
    )

    private fun candidate(
        mode: DataMode, bullish: Boolean, change: Double = 3.0,
        sufficient: Boolean = true, exchangeTime: Long = now,
        live: Boolean = true
    ) = Opportunity(
        symbol = "TEST", companyName = null, price = if (bullish) 120.0 else 80.0,
        dailyChangePct = change, score = 90, riskScore = 20, direction = "LONG",
        technicalLabel = "", volumeLabel = "", kapLabel = "", liquidityLabel = "",
        support = null, resistance = null, source = "TEST", dataTimestamp = exchangeTime,
        candles = emptyList(), technical = technical(bullish, sufficient),
        isRealtime = live, exchangeTimestamp = exchangeTime,
        dataMode = mode, signalValidity = SignalValidity.VALID,
        decisionState = DecisionState.VERIFIED_OPPORTUNITY,
        rankingScore = 88, rankingStatus = RankingStatus.CALCULATED
    )

    @Test fun dailyChangePositiveCannotOverrideBearishTrend() {
        val x = OpportunityTrendPresentation.from(candidate(DataMode.DELAYED, false, 8.0), now)
        assertEquals(TrendUiState.BEARISH, x.trend.state)
        assertFalse(x.verifiedSignal)
        assertEquals("—", x.scoreLabel)
    }

    @Test fun dailyChangeNegativeCannotOverrideBullishTrend() {
        val x = OpportunityTrendPresentation.from(candidate(DataMode.DELAYED, true, -9.0), now)
        assertEquals(TrendUiState.BULLISH, x.trend.state)
    }

    @Test fun missingIndicatorsStayGreyEvenWithRisingDailyPrice() {
        val x = OpportunityTrendPresentation.from(candidate(DataMode.DELAYED, true, sufficient = false), now)
        assertEquals(TrendUiState.INSUFFICIENT_DATA, x.trend.state)
    }

    @Test fun unverifiedProviderNeverGetsGreenSignal() {
        val x = OpportunityTrendPresentation.from(candidate(DataMode.UNVERIFIED, true), now)
        assertEquals(TrendUiState.INSUFFICIENT_DATA, x.trend.state)
        assertFalse(x.verifiedSignal)
    }

    @Test fun freshRealtimeWithVerifiedDecisionDisplaysScore() {
        val x = OpportunityTrendPresentation.from(candidate(DataMode.REALTIME, true), now)
        assertTrue(x.verifiedSignal)
        assertEquals("▲ LONG", x.signalLabel)
        assertEquals("88/100", x.scoreLabel)
    }

    @Test fun staleRealtimeNeverClaimsVerifiedSignal() {
        val x = OpportunityTrendPresentation.from(candidate(DataMode.REALTIME, true, exchangeTime = now - 61_000L), now)
        assertFalse(x.verifiedSignal)
        assertEquals("ESKİ VERİ • İŞLEM YOK", x.signalLabel)
    }

    @Test fun trendEmphasisIgnoresDailyChangeTradeDirectionAndRankingScore() {
        val original = candidate(DataMode.DELAYED, true)
        val oppositeTrade = original.copy(direction = "SHORT", dailyChangePct = -12.0, rankingScore = 1)
        val first = OpportunityTrendPresentation.from(original, now)
        val second = OpportunityTrendPresentation.from(oppositeTrade, now)
        assertEquals(first.trend, second.trend)
        assertTrue(second.trend.strengthLevel > 0)
        assertFalse(second.verifiedSignal)
        assertEquals("GECİKMELİ", second.dataLabel)
    }

    @Test fun staleOrUnverifiedEvidenceCannotKeepDirectionalEmphasis() {
        listOf(candidate(DataMode.UNVERIFIED, true),
            candidate(DataMode.REALTIME, true, exchangeTime = now - 61_000L)).forEach {
            val display = OpportunityTrendPresentation.from(it, now)
            assertEquals(0, display.trend.strengthLevel)
            assertEquals(0, display.trend.rowFillAlpha)
            assertFalse(display.verifiedSignal)
        }
    }
}
