package tr.borsatakip.v5.analysis.v540

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.V540FinalDecisionFlow
import tr.borsatakip.v5.analysis.v531.*
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.RankingStatus
import tr.borsatakip.v5.model.TechnicalSnapshot

class V540CalculationCorrectnessTest {
    private fun t(atr: Double = 2.0, support: Double? = null, resistance: Double? = null, rvol: Double? = 1.5) =
        TechnicalSnapshot(101.0,99.0,95.0,58.0,1.2,0.8,null,null,atr,100.0,rvol,support,resistance)

    @Test fun longTargetNeverCrossesVeryNearResistance() {
        val p = V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, 100.10, emptyList())!!
        assertEquals(100.10, p.target1!!, 1e-9)
        assertEquals(100.10, p.target2!!, 1e-9)
    }

    @Test fun shortTargetNeverCrossesVeryNearSupport() {
        val p = V540FinalDecisionFlow.buildRiskPlan(V531Direction.SHORT, 100.0, 2.0, 99.90, null, emptyList())!!
        assertEquals(99.90, p.target1!!, 1e-9)
        assertEquals(99.90, p.target2!!, 1e-9)
    }

    @Test fun farBarrierCannotInflateModelTarget() {
        val noBarrier = V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG,100.0,2.0,null,null,emptyList())!!
        val far = V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG,100.0,2.0,null,120.0,emptyList())!!
        assertEquals(noBarrier.target1!!, far.target1!!, 1e-9)
        assertEquals(noBarrier.target2!!, far.target2!!, 1e-9)
    }

    @Test fun mtfCannotBeInjectedIntoBaseEngine() {
        val input = baseInput().copy(mtfConsensusScore = 80, mtfAsOfTimestamp = 1_700_000_000_000L)
        var rejected = false
        try { V531CalculationEngine.calculate(input) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun contextAddsMtfExactlyOnce() {
        val base = V531CalculationEngine.calculate(baseInput())
        assertTrue(base.components.none { it.id == "MULTI_TIMEFRAME" })
        val ctx = V531ContextEngine.applyMtf(base.longScore, base.shortScore, base.dataConfidence, base.riskScore, base.availableSignalWeight, 100)
        assertEquals((base.availableSignalWeight + ScoringConfig.MULTI_TIMEFRAME_WEIGHT).coerceAtMost(100), ctx.availableSignalWeight)
    }

    @Test fun nearBarrierIsHigherRiskThanFarBarrier() {
        fun score(distance: Double) = RiskEngine.evaluate(RiskEngine.Input(100.0,2.0,0.05,80,90,null,distance,0.1,null)).score
        assertTrue(score(0.25) > score(3.5))
    }

    @Test fun freshnessHasNoBoundaryJumpAtFiveSeconds() {
        val a = V540FreshnessPolicy.score(5_000L,60_000L)
        val b = V540FreshnessPolicy.score(5_001L,60_000L)
        assertTrue(kotlin.math.abs(a-b) <= 1)
    }

    @Test fun insufficientMtfCannotProduceHighRanking() {
        val r = V540FinalDecisionFlow.finalize(V531Direction.LONG,90,90,20,100.0,t(),emptyList(),100,2,6)
        assertEquals(V531Direction.WATCH,r.publishedDirection)
        assertEquals(0,r.rankingScore)
        assertEquals(RankingStatus.INSUFFICIENT_DATA,r.rankingStatus)
    }

    @Test fun rrBlockHasDistinctRankingStatus() {
        val r = V540FinalDecisionFlow.finalize(V531Direction.LONG,90,90,20,100.0,t(resistance=100.1),emptyList(),100,6,6)
        assertEquals(0,r.rankingScore)
        assertEquals(RankingStatus.BLOCKED_RR,r.rankingStatus)
    }

    @Test fun bClassRequiresAtLeastOnePointFiveRrAndControlledRisk() {
        val r = V540FinalDecisionFlow.finalize(V531Direction.LONG,80,80,55,100.0,t(),emptyList(),100,6,6)
        assertTrue(r.qualityClass in setOf("B SINIFI","A SINIFI"))
        val weak = V540FinalDecisionFlow.finalize(V531Direction.LONG,80,80,65,100.0,t(resistance=102.8),emptyList(),100,6,6)
        assertTrue(weak.qualityClass != "B SINIFI")
    }

    @Test fun relativeLiquidityDoesNotDependOnAbsoluteContractVolume() {
        val low = V540LiquidityEngine.score(0.5,0.10)
        val high = V540LiquidityEngine.score(2.0,0.10)
        assertNotNull(low); assertNotNull(high); assertTrue(high!! > low!!)
    }

    @Test fun longBarrierExactlyQuarterAtrIsNeverCrossed() {
        val p = V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, 100.50, emptyList())!!
        val t1=p.target1!!; val t2=p.target2!!
        assertTrue(t1 <= 100.50 + 1e-9)
        assertTrue(t2 <= 100.50 + 1e-9)
        assertTrue(t1 <= t2 + 1e-9)
        assertTrue((p.rr1 ?: 0.0) > 0.0 && (p.rr2 ?: 0.0) >= (p.rr1 ?: 0.0))
    }

    @Test fun shortBarrierExactlyQuarterAtrIsNeverCrossed() {
        val p = V540FinalDecisionFlow.buildRiskPlan(V531Direction.SHORT, 100.0, 2.0, 99.50, null, emptyList())!!
        val t1=p.target1!!; val t2=p.target2!!
        assertTrue(t1 >= 99.50 - 1e-9)
        assertTrue(t2 >= 99.50 - 1e-9)
        assertTrue(t2 <= t1 + 1e-9)
        assertTrue((p.rr1 ?: 0.0) > 0.0 && (p.rr2 ?: 0.0) >= (p.rr1 ?: 0.0))
    }

    @Test fun absentBarrierUsesCanonicalModelTargets() {
        val p = V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, null, emptyList())!!
        assertEquals(103.0, p.target1!!, 1e-9)
        assertEquals(105.0, p.target2!!, 1e-9)
    }

    private fun baseInput(): V531CalculationInput {
        val c=(0 until 240).map { i -> val p=100.0+i*0.2; Candle(1_700_000_000_000L+i*60_000L,p-0.1,p+0.4,p-0.4,p,1000.0+i) }
        val last=c.last().close
        return V531CalculationInput(
            c,t(atr=2.0,rvol=1.5).copy(ema20=last-2,ema50=last-5,ema200=last-10),last,
            V531DataQualityInput(1000,240,true,true,true,true,true,false,false),
            spreadPct=0.05,liquidityScore=80,structureDistanceAtr=2.0,
            evaluationTimestamp=c.last().timestamp,technicalAsOfTimestamp=c.last().timestamp
        )
    }
}
