package tr.borsatakip.v5.analysis.v541

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.v540.V540LiquidityEngine
import tr.borsatakip.v5.analysis.v540.backtest.*
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.RiskPlan

class V541BacktestExecutionCorrectnessTest {
    private val t0 = 1_700_000_000_000L

    private fun signal(
        future: List<Candle>,
        direction: String = "LONG",
        entry: Double = 100.0,
        plan: RiskPlan = if (direction == "LONG") RiskPlan(100.0,98.0,103.0,105.0,1.5,2.5) else RiskPlan(100.0,102.0,97.0,95.0,1.5,2.5),
        intrabar: List<Candle> = emptyList(),
        score: Int = 80
    ) = V540BacktestSignal(
        signalId="s1",symbol="THYAO",instrumentType="BIST",timeframeMinutes=5,signalTimestamp=t0,
        direction=direction,signalScore=score,confidence=85,riskScore=30,rankingScore=75,entryPrice=entry,
        riskPlan=plan,mtfScore=0,marketRegime="NORMAL",futureCandles=future,
        liquidityScore=80,spreadPct=0.10,volatilityPct=2.0,orderSizeNotional=10_000.0,averageDailyNotional=1_000_000.0,
        intrabarCandles=intrabar
    )

    private fun bar(i:Int,o:Double,h:Double,l:Double,c:Double) = Candle(t0+i*300_000L,o,h,l,c,1000.0)

    @Test fun canonicalEntryMustMatchRiskPlanEntry() {
        val s=signal(listOf(bar(1,100.0,104.0,99.0,103.0)),plan=RiskPlan(101.0,98.0,103.0,105.0,1.5,2.5))
        assertEquals(V540BacktestEvent.INVALID,V540BacktestEngine.evaluate(s,V540ExecutionCostConfig()).event)
    }

    @Test fun longRiskGeometryIsValidated() {
        val bad=RiskPlan(100.0,101.0,103.0,105.0,1.5,2.5)
        assertEquals(V540BacktestEvent.INVALID,V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,104.0,99.0,103.0)),plan=bad),V540ExecutionCostConfig()).event)
    }

    @Test fun shortRiskGeometryIsValidated() {
        val bad=RiskPlan(100.0,98.0,97.0,95.0,1.5,2.5)
        assertEquals(V540BacktestEvent.INVALID,V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,101.0,96.0,97.0)),direction="SHORT",plan=bad),V540ExecutionCostConfig()).event)
    }

    @Test fun nextBarOpenUsesActualExecutionAndRebasesPlan() {
        val future=listOf(bar(1,102.0,104.0,101.0,103.0))
        val policy=V540ExecutionPolicy(entryMode=V540EntryExecutionMode.NEXT_BAR_OPEN,closeRemainderAtHorizon=true)
        val r=V540BacktestEngine.evaluate(signal(future),V540ExecutionCostConfig(),policy)
        assertEquals(102.0,r.entryPrice,1e-9)
        val plan = r.executedRiskPlan!!
        assertEquals(102.0,plan.entry,1e-9)
        assertEquals(100.0,plan.stop!!,1e-9)
        assertEquals(105.0,plan.target1!!,1e-9)
    }

    @Test fun rejectOnEntryMismatchIsFailClosed() {
        val policy=V540ExecutionPolicy(entryMode=V540EntryExecutionMode.NEXT_BAR_OPEN,riskPlanRebaseMode=V540RiskPlanRebaseMode.REJECT_ON_MISMATCH)
        val r=V540BacktestEngine.evaluate(signal(listOf(bar(1,102.0,104.0,101.0,103.0))),V540ExecutionCostConfig(),policy)
        assertEquals(V540BacktestEvent.ENTRY_REJECTED,r.event)
    }

    @Test fun limitOrderCanRemainUnfilled() {
        val p=V540ExecutionPolicy(entryMode=V540EntryExecutionMode.LIMIT_AT_ENTRY,maxEntryWaitBars=2)
        val r=V540BacktestEngine.evaluate(signal(listOf(bar(1,102.0,104.0,101.0,103.0),bar(2,103.0,105.0,101.5,104.0))),V540ExecutionCostConfig(),p)
        assertEquals(V540BacktestEvent.NO_FILL,r.event)
    }

    @Test fun limitFillBarWithExitAndNoIntrabarIsAmbiguous() {
        val p=V540ExecutionPolicy(entryMode=V540EntryExecutionMode.LIMIT_AT_ENTRY)
        val r=V540BacktestEngine.evaluate(signal(listOf(bar(1,101.0,103.5,97.5,100.0))),V540ExecutionCostConfig(),p)
        assertEquals(V540BacktestEvent.AMBIGUOUS,r.event)
    }

    @Test fun intrabarCanResolveTargetThenStop() {
        val parent=bar(1,101.0,103.5,97.5,100.0)
        val base=parent.timestamp
        val intrabar=listOf(
            Candle(base,101.0,101.2,99.9,100.2,100.0),
            Candle(base+60_000,100.2,103.2,100.1,103.0,100.0),
            Candle(base+120_000,103.0,103.1,97.8,98.0,100.0)
        )
        val p=V540ExecutionPolicy(entryMode=V540EntryExecutionMode.LIMIT_AT_ENTRY,target1ExitFraction=0.5,intrabarResolutionMode=V540IntrabarResolutionMode.USE_INTRABAR_IF_AVAILABLE)
        val r=V540BacktestEngine.evaluate(signal(listOf(parent),intrabar=intrabar),V540ExecutionCostConfig(),p)
        assertEquals(V540BacktestEvent.TARGET1_THEN_STOP,r.event)
        assertEquals("INTRABAR_ORDER_RESOLVED",r.verification)
    }

    @Test fun mfeUsesActualExecutionPriceAfterGap() {
        val p=V540ExecutionPolicy(entryMode=V540EntryExecutionMode.NEXT_BAR_OPEN,closeRemainderAtHorizon=true)
        val r=V540BacktestEngine.evaluate(signal(listOf(bar(1,102.0,104.0,101.0,103.0))),V540ExecutionCostConfig(),p)
        assertEquals((104.0/102.0-1.0)*100.0,r.mfePct!!,1e-9)
    }

    @Test fun adaptiveCostsIncreaseForIlliquidFastTrade() {
        val liquid=V541AdaptiveCostModel(2.0).estimate(V541CostContext("X","VIOP",60,100.0,95,0.02,1.0,1_000.0,10_000_000.0)).config.totalCostPct()
        val illiquid=V541AdaptiveCostModel(2.0).estimate(V541CostContext("X","VIOP",1,100.0,20,0.40,6.0,1_000_000.0,2_000_000.0)).config.totalCostPct()
        assertTrue(illiquid>liquid)
    }

    @Test fun metricsAreChronologicalRegardlessOfInputOrder() {
        val base=V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,103.5,99.0,103.0))),V540ExecutionCostConfig())
        val a=base.copy(signalId="a",entryTime=t0+1,netReturnPct=-10.0)
        val b=base.copy(signalId="b",entryTime=t0+2,netReturnPct=20.0)
        val c=base.copy(signalId="c",entryTime=t0+3,netReturnPct=-5.0)
        assertEquals(V540BacktestEngine.metrics(listOf(a,b,c)).maxDrawdownPct,V540BacktestEngine.metrics(listOf(c,a,b)).maxDrawdownPct)
    }

    @Test fun blockBootstrapIsDeterministicAndLabeled() {
        val base=V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,103.5,99.0,103.0))),V540ExecutionCostConfig())
        val trades=(0 until 10).map { i -> base.copy(signalId="s$i",symbol=if(i%2==0)"A" else "B",entryTime=t0+i*1000L,netReturnPct=if(i%2==0)1.0 else -0.5) }
        val x=V540WalkForwardEngine.monteCarloBlock(trades,V541BootstrapMode.SYMBOL_BLOCK,100,20,7)
        val y=V540WalkForwardEngine.monteCarloBlock(trades,V541BootstrapMode.SYMBOL_BLOCK,100,20,7)
        assertEquals(x,y); assertEquals("SYMBOL_BLOCK",x.bootstrapMode)
    }

    @Test fun oosReturnsCannotInfluenceSelectedModel() {
        fun trade(id:String,time:Long,r:Double)=V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,103.5,99.0,103.0))),V540ExecutionCostConfig()).copy(signalId=id,entryTime=time,netReturnPct=r)
        val times=(0 until 12).map { t0+it*10_000L }
        val a=times.mapIndexed { i,t -> trade("a$i",t,if(i<4)2.0 else if(i<7)1.0 else -50.0) }
        val b=times.mapIndexed { i,t -> trade("b$i",t,if(i<4)0.5 else if(i<7)1.0 else 50.0) }
        val first=V540WalkForwardEngine.optimizeAndEvaluate(listOf(V541ModelCandidate("A",a),V541ModelCandidate("B",b)),40_000,30_000,30_000,20_000,2,1)
        val a2=a.map { if(it.entryTime>=t0+70_000) it.copy(netReturnPct=99.0) else it }
        val b2=b.map { if(it.entryTime>=t0+70_000) it.copy(netReturnPct=-99.0) else it }
        val second=V540WalkForwardEngine.optimizeAndEvaluate(listOf(V541ModelCandidate("A",a2),V541ModelCandidate("B",b2)),40_000,30_000,30_000,20_000,2,1)
        assertEquals(first.map{it.selectedModelId},second.map{it.selectedModelId})
    }

    @Test fun evidenceGateCannotPassWithoutRealEvidence() {
        val report=V541EvidenceGate.assess(emptyList(),emptyList(),false,false,V541EvidenceRequirements(minClosedTrades=1,minOosWindows=1))
        assertEquals(V541ValidationStatus.INSUFFICIENT_EVIDENCE,report.status)
        assertTrue(report.reasons.contains("POINT_IN_TIME_DATA_NOT_VERIFIED"))
        assertTrue(report.reasons.contains("OBSERVED_COST_INPUTS_NOT_VERIFIED"))
    }

    @Test fun scoreCalibrationDoesNotClaimProbabilityWithTinySample() {
        val report=V541EvidenceGate.scoreCalibration(emptyList(),minSamplesPerBucket=10,minQualifyingBuckets=2)
        assertEquals(V541ValidationStatus.INSUFFICIENT_EVIDENCE,report.status)
        assertEquals("SCORE_IS_NOT_PROBABILITY",report.note)
    }


    @Test fun rrMetadataMismatchIsRejected() {
        val bad=RiskPlan(100.0,98.0,103.0,105.0,9.0,9.0)
        val r=V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,104.0,99.0,103.0)),plan=bad),V540ExecutionCostConfig())
        assertEquals(V540BacktestEvent.INVALID,r.event)
    }

    @Test fun entrySlippageIsAdverseForLongAndShort() {
        val p=V540ExecutionPolicy(entryMode=V540EntryExecutionMode.NEXT_BAR_OPEN_WITH_SLIPPAGE,entrySlippageBpsOneWay=100.0,closeRemainderAtHorizon=true)
        val long=V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,102.0,99.0,101.0))),V540ExecutionCostConfig(),p)
        val short=V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,101.0,98.0,99.0)),direction="SHORT"),V540ExecutionCostConfig(),p)
        assertEquals(101.0,long.entryPrice,1e-9)
        assertEquals(99.0,short.entryPrice,1e-9)
    }

    @Test fun rollingLiquidityPercentileDifferentiatesRelativeContext() {
        val hist=(1..100).map { it.toDouble() }
        val low=V540LiquidityEngine.score(1.0,0.10,10.0,hist)!!
        val high=V540LiquidityEngine.score(1.0,0.10,90.0,hist)!!
        assertTrue(high>low)
    }

    @Test fun adaptiveCostEvidenceMarksMissingObservedInputs() {
        val e=V541AdaptiveCostModel(2.0).estimate(V541CostContext("X","BIST",5,100.0,null,null,null,null,null))
        assertTrue(e.evidence.contains("SPREAD_MISSING"))
        assertTrue(e.evidence.contains("LIQUIDITY_MISSING"))
        assertTrue(e.evidence.contains("PARTICIPATION_MISSING"))
    }

    @Test fun monteCarloDayBlockIsDifferentModeFromIid() {
        val base=V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,103.5,99.0,103.0))),V540ExecutionCostConfig())
        val trades=(0 until 8).map { i -> base.copy(signalId="d$i",entryTime=t0+(i/2)*86_400_000L+i,netReturnPct=if(i%2==0)2.0 else -1.0) }
        val iid=V540WalkForwardEngine.monteCarloBlock(trades,V541BootstrapMode.TRADE_IID,50,8,3)
        val day=V540WalkForwardEngine.monteCarloBlock(trades,V541BootstrapMode.DAY_BLOCK,50,8,3)
        assertEquals("TRADE_IID",iid.bootstrapMode)
        assertEquals("DAY_BLOCK",day.bootstrapMode)
    }

    @Test fun signalDecayIsExplicitlyNotExecutedTradeOutcome() {
        val future=(1..3).map { i -> bar(i,100.0,101.0,99.0,100.0+i) }
        val p=V540SignalDecayEngine.evaluate(listOf(signal(future)),V540ExecutionCostConfig(),listOf(1)).single()
        assertEquals("SIGNAL_DECAY_CLOSE_TO_CLOSE",p.measurementType)
        assertFalse(p.executionComparable)
    }
}
