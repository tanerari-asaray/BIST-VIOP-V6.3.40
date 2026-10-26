package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.analysis.v531.V531Direction
import tr.borsatakip.v5.model.TechnicalSnapshot
import tr.borsatakip.v5.model.RankingStatus

class ViopTarget1RiskPlanTest {
    private val floor = ViopRiskRewardPolicy.MIN_TARGET1_GROSS_RR

    @Test fun legacyBistPlanRemainsUnchanged() {
        val p = V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, null, emptyList())!!
        assertEquals(103.0, p.target1!!, 1e-9)
        assertEquals(1.2, p.rr1!!, 1e-9)
    }

    @Test fun viopLongMinimumIsMetWithoutBeyondT2() {
        val p = V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, null, emptyList(), floor)!!
        assertEquals(103.75, p.target1!!, 1e-9)
        assertEquals(105.0, p.target2!!, 1e-9)
        assertEquals(1.5, p.rr1!!, 1e-9)
    }

    @Test fun viopShortMinimumIsSymmetric() {
        val p = V540FinalDecisionFlow.buildRiskPlan(V531Direction.SHORT, 100.0, 2.0, null, null, emptyList(), floor)!!
        assertEquals(96.25, p.target1!!, 1e-9)
        assertEquals(95.0, p.target2!!, 1e-9)
        assertEquals(1.5, p.rr1!!, 1e-9)
    }

    @Test fun knownResistanceBlocksUnattainableLongPlan() {
        assertNull(V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, 103.5, emptyList(), floor))
    }

    @Test fun knownSupportBlocksUnattainableShortPlan() {
        assertNull(V540FinalDecisionFlow.buildRiskPlan(V531Direction.SHORT, 100.0, 2.0, 96.5, null, emptyList(), floor))
    }

    @Test fun overlyWideStopCannotInventExtensionPastTwoPointFiveAtr() {
        assertNull(V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, 96.5, null, emptyList(), floor))
        assertNull(V540FinalDecisionFlow.buildRiskPlan(V531Direction.SHORT, 100.0, 2.0, null, 103.5, emptyList(), floor))
    }

    @Test fun rejectedPlanHasDistinctViopRrAuditCodeAndBlockedRanking() {
        val technical = TechnicalSnapshot(
            101.0, 99.0, 95.0, 58.0, 1.2, 0.8, null, null,
            2.0, 100.0, 1.5, null, 103.5
        )
        val result = V540FinalDecisionFlow.finalize(
            V531Direction.LONG, 90, 90, 20, 100.0, technical, emptyList(),
            100, 6, 6, minFirstTargetGrossRr = floor
        )
        assertEquals("VIOP_T1_RR_NOT_FEASIBLE", result.rrGateCode)
        assertEquals(RankingStatus.BLOCKED_RR, result.rankingStatus)
        assertEquals(V531Direction.WATCH, result.publishedDirection)
    }

    @Test fun unavailableOrInvalidRrRuleCannotPublish() {
        assertNull(V540FinalDecisionFlow.buildRiskPlan(V531Direction.LONG, 100.0, 2.0, null, null, emptyList(), Double.NaN))
    }
}
