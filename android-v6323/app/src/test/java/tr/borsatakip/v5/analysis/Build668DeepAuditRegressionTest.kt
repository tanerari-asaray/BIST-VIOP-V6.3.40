package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.*

class Build668DeepAuditRegressionTest {
    private fun row(direction: String, validity: SignalValidity, decision: DecisionState) = Opportunity(
        symbol="ASELS", companyName="ASELS", price=100.0, dailyChangePct=1.0, score=90, riskScore=15,
        direction=direction, technicalLabel="", volumeLabel="", kapLabel="", liquidityLabel="",
        support=95.0, resistance=110.0, source="test", dataTimestamp=1000L,
        candles=emptyList(), technical=TechnicalSnapshot(null,null,null,null,null,null,null,null,null,null,null,null,null),
        dataMode=DataMode.REALTIME, isRealtime=true, finalSignalScore=95, rankingScore=88,
        longScore=95, shortScore=0, signalValidity=validity, decisionState=decision,
        riskPlan=RiskPlan(100.0,95.0,110.0,120.0,2.0,4.0)
    )

    @Test fun watchNeverKeepsTradeDirectionOrPlanAfterOptimization() {
        val watched=row("LONG",SignalValidity.WATCH,DecisionState.WATCH)
        val safe=OpportunityPublicationPolicy.enforceVerifiedPublication(watched)
        assertEquals("NEUTRAL",safe.direction)
        assertNull(safe.riskPlan)
        assertEquals(0,safe.finalSignalScore)
        assertEquals(0,safe.rankingScore)
        assertEquals(DecisionState.WATCH,safe.decisionState)
    }
    @Test fun evenNominallyValidUnpublishedSignalIsNotTradeEligible() {
        val x=row("SHORT",SignalValidity.VALID,DecisionState.VERIFIED_OPPORTUNITY)
        val safe=OpportunityPublicationPolicy.enforceVerifiedPublication(x)
        assertEquals("NEUTRAL",safe.direction)
        assertEquals(SignalValidity.WATCH,safe.signalValidity)
        assertNull(safe.riskPlan)
    }
    @Test fun watchedLongIsNeverInLongOrHighPowerFilter() {
        val x=row("LONG",SignalValidity.WATCH,DecisionState.WATCH)
        assertTrue(OpportunityFilterPolicy.apply(listOf(x),OpportunityFilter.LONG).isEmpty())
        assertTrue(OpportunityFilterPolicy.apply(listOf(x),OpportunityFilter.HIGH_POWER).isEmpty())
        assertEquals(1,OpportunityFilterPolicy.apply(listOf(x),OpportunityFilter.ALL).size)
    }
    @Test fun duplicateOrBackwardOrFutureCandlesAreNeverScored() {
        val now=System.currentTimeMillis()
        val c=(0 until 240).map { i ->
            val p=80.0+i*0.1
            Candle(now-(240L-i)*60_000L,p,p+1.0,p-1.0,p,1000.0)
        }
        val base=Stock("TEST","Test",c,"unit",now,interval="1m",lastBarClosed=false)
        assertNull(OpportunityEngine.score(base.copy(candles=c.toMutableList().also { it[100]=it[99].copy() })))
        assertNull(OpportunityEngine.score(base.copy(candles=c.reversed())))
        assertNull(OpportunityEngine.score(base.copy(candles=c.toMutableList().also { it[239]=it[239].copy(timestamp=now+60_000L) })))
    }
}
