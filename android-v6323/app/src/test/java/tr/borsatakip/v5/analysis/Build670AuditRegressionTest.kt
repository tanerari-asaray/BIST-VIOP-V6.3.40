package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.analysis.v540.backtest.*
import tr.borsatakip.v5.data.LiveCandlePolicy
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.RiskPlan

class Build670AuditRegressionTest {
    private val t = 1_700_000_000_000L
    private fun bar(i: Int, o: Double, h: Double, l: Double, c: Double) = Candle(t + i * 300_000L, o, h, l, c, 1000.0)
    private fun signal(bars: List<Candle>, short: Boolean = false) = V540BacktestSignal(
        "audit", "TEST", "BIST", 5, t, if (short) "SHORT" else "LONG", 80, 90, 20, 80, 100.0,
        if (short) RiskPlan(100.0, 105.0, 95.0, 90.0, 1.0, 2.0) else RiskPlan(100.0, 95.0, 105.0, 110.0, 1.0, 2.0),
        futureCandles = bars)
    private fun eval(bars: List<Candle>, fraction: Double = 1.0, short: Boolean = false) =
        V540BacktestEngine.evaluate(signal(bars, short), V540ExecutionCostConfig(), V540ExecutionPolicy(target1ExitFraction = fraction))

    @Test fun firstLossIsMeasuredAgainstInitialCapital() {
        val trade = eval(listOf(bar(1, 100.0, 101.0, 94.0, 95.0))).copy(netReturnPct = -10.0)
        assertEquals(10.0, V540BacktestEngine.metrics(listOf(trade)).maxDrawdownPct!!, 1e-9)
        assertEquals(19.0, V540BacktestEngine.metrics(listOf(trade, trade.copy(signalId="second", entryTime=t+1))).maxDrawdownPct!!, 1e-9)
    }
    @Test fun fullT1ExitCannotBeUpgradedToT2InSameBar() {
        val out = eval(listOf(bar(1, 100.0, 111.0, 99.0, 108.0)))
        assertEquals(V540BacktestEvent.TARGET1, out.event)
        assertEquals(5.0, out.grossReturnPct!!, 1e-9)
    }
    @Test fun partialT1T2UsesWeightedFillsInSameBar() {
        assertEquals(7.5, eval(listOf(bar(1, 100.0, 111.0, 99.0, 108.0)), 0.5).grossReturnPct!!, 1e-9)
    }
    @Test fun shortFullAndPartialTargetsAreSymmetric() {
        val bars = listOf(bar(1, 100.0, 101.0, 89.0, 92.0))
        assertEquals(5.0, eval(bars, short=true).grossReturnPct!!, 1e-9)
        assertEquals(7.5, eval(bars, 0.5, true).grossReturnPct!!, 1e-9)
    }
    @Test fun longGapStopUsesObservedOpenInsteadOfUntradedStop() {
        val out = eval(listOf(bar(1, 90.0, 92.0, 88.0, 91.0)))
        assertEquals(90.0, out.exitPrice!!, 0.0)
        assertEquals(-10.0, out.maePct!!, 1e-9)
        assertEquals(0.0, out.mfePct!!, 1e-9)
        assertEquals("STOP_MARKET_GAP_OPEN_REFERENCE", out.verification)
    }
    @Test fun shortGapStopUsesObservedOpen() {
        assertEquals(110.0, eval(listOf(bar(1, 110.0, 112.0, 108.0, 111.0)), short=true).exitPrice!!, 0.0)
    }
    @Test fun partialExitThenGapUsesOnlyRemainingQuantity() {
        val out = eval(listOf(bar(1, 100.0, 106.0, 99.0, 105.0), bar(2, 90.0, 92.0, 88.0, 91.0)), 0.5)
        assertEquals(-2.5, out.grossReturnPct!!, 1e-9)
    }
    @Test fun trainingSelectionCannotUseOutcomeBeyondItsBoundary() {
        val base = eval(listOf(bar(1, 100.0, 106.0, 99.0, 105.0)))
        fun trade(id: String, entry: Long, exit: Long, result: Double) = base.copy(signalId=id, entryTime=t+entry*1000, exitTime=t+exit*1000, netReturnPct=result, timeframeMinutes=1)
        val known = listOf(trade("a",0,1,1.0), trade("v",110,111,1.0), trade("o",210,211,1.0), trade("end",310,311,1.0))
        fun select(value: Double) = V540WalkForwardEngine.optimizeAndEvaluate(
            listOf(V541ModelCandidate("A",known+trade("future",90,150,value)), V541ModelCandidate("B",known.map { it.copy(netReturnPct=0.5) })),
            100_000,100_000,100_000,100_000,1,1).first()
        assertEquals("A", select(-1000.0).selectedModelId)
        assertEquals(select(-1000.0).train.expectancyPct, select(1000.0).train.expectancyPct)
    }
    @Test fun liveCandlesRejectInvalidGeometryAndTime() {
        val valid = Candle(t,100.0,101.0,99.0,100.0,100.0)
        assertTrue(LiveCandlePolicy.accepts(valid,"1m","1m",t+30_000))
        for (bad in listOf(valid.copy(high=90.0),valid.copy(timestamp=t+86_400_000),valid.copy(timestamp=t-300_000),valid.copy(volume=-1.0)))
            assertFalse(LiveCandlePolicy.accepts(bad,"1m","1m",t+30_000))
        assertFalse(LiveCandlePolicy.accepts(valid,"5m","1m",t+30_000))
    }
    @Test fun analyzerRejectsMalformedDuplicateAndReversedSeries() {
        val bars = (1..230).map { bar(it,100.0,101.0,99.0,100.0) }
        assertNotNull(TechnicalAnalyzer.analyze(bars).ema20)
        assertNull(TechnicalAnalyzer.analyze(bars.reversed()).ema20)
        assertNull(TechnicalAnalyzer.analyze(bars+bars.last()).ema20)
        assertNull(TechnicalAnalyzer.analyze(bars.dropLast(1)+bars.last().copy(close=200.0)).ema20)
    }
    @Test fun newsCoverageMustBeVerifiedAndKnownAtDecisionTime() {
        val clear = BistNewsRisk("TEST","CLEAR",t,t,"TEST PROVIDER",true)
        assertTrue(clear.knownFor("TEST",t))
        assertFalse(clear.copy(availableAt=t+1).knownFor("TEST",t))
        assertFalse(clear.copy(verified=false).knownFor("TEST",t))
        assertFalse(clear.copy(coverageThrough=t-1).knownFor("TEST",t))
        assertFalse(clear.knownFor("OTHER",t))
    }
    @Test fun unknownOrCriticalNewsVetoesHighScore() {
        val e = BistPublicationPolicy.Evidence(true,true,true,true,true,true,true,false,true,true,true,true,40_000_000.0,2.0,true,0.0,false)
        assertFalse(BistPublicationPolicy.assess(e).eligible)
        assertTrue(BistPublicationPolicy.assess(e.copy(newsRiskKnown=true)).eligible)
        assertFalse(BistPublicationPolicy.assess(e.copy(newsRiskKnown=true,criticalNewsRisk=true)).eligible)
    }
    @Test fun dailyProfileUsesSma200AndCloseBasedBreakout() {
        val bars = (1..230).map { i -> Candle(t+i*86_400_000L,i+100.0,i+200.0,i+90.0,i+100.0,1000.0) }
        val result = BistDailyProfile.evaluate(bars,bars,true,2.0)
        assertEquals(bars.takeLast(200).map { it.close }.average(),result.sma200!!,1e-9)
        assertEquals("BREAKOUT",result.model)
        assertTrue(result.market)
        assertTrue(result.return5Pct!!>0)
        assertTrue(result.return20Pct!!>0)
    }
    @Test fun optimisticCollisionCannotFillAnUntouchedSecondTarget() {
        val out = V540BacktestEngine.evaluate(signal(listOf(bar(1,100.0,106.0,94.0,100.0))),
            V540ExecutionCostConfig(), V540ExecutionPolicy(target1ExitFraction=0.5,
                intrabarResolutionMode=V540IntrabarResolutionMode.OPTIMISTIC_TARGET_FIRST))
        assertEquals(V540BacktestEvent.TARGET1_THEN_STOP,out.event)
        assertEquals(0.0,out.grossReturnPct!!,1e-9)
    }
    @Test fun barOpenTimestampIsNotTheTimeTheExitOutcomeBecomesKnown() {
        val trade = eval(listOf(bar(1,100.0,106.0,99.0,105.0)))
        assertEquals(trade.exitTime!!+300_000L,trade.outcomeKnownAt!!)
        val gap = eval(listOf(bar(1,90.0,92.0,88.0,91.0)))
        assertEquals(gap.exitTime,gap.outcomeKnownAt)
    }
}
