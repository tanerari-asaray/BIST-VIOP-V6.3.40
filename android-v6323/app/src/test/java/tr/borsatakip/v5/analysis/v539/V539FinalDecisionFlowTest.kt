package tr.borsatakip.v5.analysis.v539

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.V539FinalDecisionFlow
import tr.borsatakip.v5.analysis.OpportunityRankingPolicy
import tr.borsatakip.v5.analysis.v531.ScoringConfig
import tr.borsatakip.v5.analysis.v531.V531Direction
import tr.borsatakip.v5.analysis.v531.VolatilityEngine
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.TechnicalSnapshot

class V539FinalDecisionFlowTest {
    private fun technical(
        atr: Double? = 2.0,
        support: Double? = null,
        resistance: Double? = null,
        volumeRatio: Double? = 1.5
    ) = TechnicalSnapshot(
        ema20 = 101.0, ema50 = 99.0, ema200 = 95.0, rsi14 = 58.0,
        macd = 1.2, macdSignal = 0.8, bbUpper = null, bbLower = null,
        atr14 = atr, vwap = 100.0, volumeRatio = volumeRatio,
        support = support, resistance = resistance
    )

    private fun candles(close: Double = 100.0, count: Int = 30): List<Candle> =
        (0 until count).map { i ->
            val p = close - (count - 1 - i) * 0.05
            Candle(1_700_000_000_000L + i * 60_000L, p, p + 0.4, p - 0.4, p, 10_000.0 + i)
        }

    @Test fun longFinalDirectionCreatesOnlyLongRiskPlan() {
        val r = V539FinalDecisionFlow.finalize(V531Direction.LONG, 82, 90, 30, 100.0, technical(), emptyList(), 100)
        assertEquals(V531Direction.LONG, r.engineDirection)
        assertEquals(V531Direction.LONG, r.publishedDirection)
        val plan = r.riskPlan
        assertNotNull(plan)
        assertTrue(plan!!.stop!! < 100.0)
        assertTrue(plan.target1!! > 100.0)
    }

    @Test fun shortFinalDirectionCreatesOnlyShortRiskPlan() {
        val r = V539FinalDecisionFlow.finalize(V531Direction.SHORT, 82, 90, 30, 100.0, technical(), emptyList(), 100)
        assertEquals(V531Direction.SHORT, r.publishedDirection)
        val plan = r.riskPlan
        assertNotNull(plan)
        assertTrue(plan!!.stop!! > 100.0)
        assertTrue(plan.target1!! < 100.0)
    }

    @Test fun watchHasNoRiskPlanAndZeroRanking() {
        val r = V539FinalDecisionFlow.finalize(V531Direction.WATCH, 90, 95, 10, 100.0, technical(), candles(), 100)
        assertEquals(V531Direction.WATCH, r.publishedDirection)
        assertNull(r.riskPlan)
        assertEquals(0, r.rankingScore)
    }

    @Test fun lowRiskRewardBlocksPublicationAndRanking() {
        val r = V539FinalDecisionFlow.finalize(
            V531Direction.LONG, 90, 95, 10, 100.0,
            technical(resistance = 100.8), candles(), 100
        )
        assertTrue((r.rr1 ?: 99.0) < 1.0)
        assertEquals(V531Direction.WATCH, r.publishedDirection)
        assertEquals("RR_BELOW_1_BLOCK", r.rrGateCode)
        assertNull(r.riskPlan)
        assertEquals(0, r.rankingScore)
    }

    @Test fun structuralResistanceCanOnlyShortenLongTarget() {
        val noStructure = V539FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, null, emptyList())!!
        val farResistance = V539FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, 120.0, emptyList())!!
        val nearResistance = V539FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, 101.0, emptyList())!!
        assertEquals(noStructure.target1!!, farResistance.target1!!, 0.0001)
        assertTrue(nearResistance.target1!! <= noStructure.target1!!)
    }

    @Test fun structuralSupportCanOnlyShortenShortTarget() {
        val noStructure = V539FinalDecisionFlow.buildRiskPlan(V531Direction.SHORT, 100.0, 2.0, null, null, emptyList())!!
        val farSupport = V539FinalDecisionFlow.buildRiskPlan(V531Direction.SHORT, 100.0, 2.0, 80.0, null, emptyList())!!
        val nearSupport = V539FinalDecisionFlow.buildRiskPlan(V531Direction.SHORT, 100.0, 2.0, 99.0, null, emptyList())!!
        assertEquals(noStructure.target1!!, farSupport.target1!!, 0.0001)
        assertTrue(nearSupport.target1!! >= noStructure.target1!!)
    }

    @Test fun rankingUsesFinalSignalScoreNotLegacyScore() {
        val low = OpportunityRankingPolicy.calculateFinal(60, 80, 30, 2.0, 1.5, 100)
        val high = OpportunityRankingPolicy.calculateFinal(90, 80, 30, 2.0, 1.5, 100)
        assertTrue(high > low)
    }

    @Test fun volatilityCanUseFullDeclaredTenPointWeight() {
        val c = listOf(
            Candle(1, 100.0, 101.0, 99.0, 100.0, 1.0),
            Candle(2, 101.0, 102.0, 100.0, 101.0, 1.0),
            Candle(3, 102.0, 103.0, 101.0, 102.0, 1.0),
            Candle(4, 103.0, 104.0, 102.0, 103.0, 1.0),
            Candle(5, 104.0, 105.0, 103.0, 104.0, 1.0),
            Candle(6, 108.0, 110.0, 107.0, 110.0, 1.0)
        )
        val component = VolatilityEngine.evaluate(c, technical(atr = 2.0), 110.0)
        assertEquals(ScoringConfig.VOLATILITY_WEIGHT, component.weight)
        assertEquals(ScoringConfig.VOLATILITY_WEIGHT, component.longContribution)
    }

    @Test fun rrBetweenOneAndOnePointFiveKeepsDirectionButMarksPenaltyBand() {
        val gate = V539RiskRewardGate.evaluate(V531Direction.LONG, 1.25)
        assertEquals(V531Direction.LONG, gate.publishedDirection)
        assertEquals("RR_1_0_1_5_PENALIZED", gate.code)
    }
}
