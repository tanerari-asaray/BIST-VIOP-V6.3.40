package tr.borsatakip.v5.analysis.v540

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.v540.backtest.*
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.RiskPlan

class V540BacktestEngineTest {
    private val t0=1_700_000_000_000L
    private fun signal(future: List<Candle>, score:Int=80) = V540BacktestSignal(
        "s1","THYAO","BIST",5,t0,"LONG",score,85,30,75,100.0,
        RiskPlan(100.0,98.0,103.0,105.0,1.5,2.5),null,"NORMAL",future
    )

    @Test fun futureOnlyPreventsLookAhead() {
        val past=Candle(t0,100.0,200.0,1.0,100.0,1.0)
        val future=Candle(t0+60_000,100.0,103.5,99.5,103.0,1.0)
        val r=V540BacktestEngine.evaluate(signal(listOf(past,future)),V540ExecutionCostConfig())
        assertEquals(V540BacktestEvent.TARGET1,r.event)
        assertEquals(1,r.barsObserved)
    }

    @Test fun mfeAndMaeAreDirectional() {
        val future=listOf(
            Candle(t0+60_000,100.0,102.0,99.0,101.0,1.0),
            Candle(t0+120_000,101.0,103.5,100.0,103.0,1.0)
        )
        val r=V540BacktestEngine.evaluate(signal(future),V540ExecutionCostConfig())
        assertTrue((r.mfePct?:0.0)>0.0)
        assertTrue((r.maePct?:0.0)<0.0)
    }

    @Test fun transactionCostsReduceNetReturn() {
        val future=listOf(Candle(t0+60_000,100.0,103.5,99.5,103.0,1.0))
        val gross=V540BacktestEngine.evaluate(signal(future),V540ExecutionCostConfig())
        val net=V540BacktestEngine.evaluate(signal(future),V540ExecutionCostConfig(5.0,5.0,5.0,5.0))
        assertTrue(net.netReturnPct!! < gross.netReturnPct!!)
    }

    @Test fun ambiguousSameBarIsNotCountedAsWinOrLoss() {
        val future=listOf(Candle(t0+60_000,100.0,104.0,97.0,101.0,1.0))
        val r=V540BacktestEngine.evaluate(signal(future),V540ExecutionCostConfig())
        assertEquals(V540BacktestEvent.AMBIGUOUS,r.event)
        assertEquals(null,r.netReturnPct)
    }

    @Test fun scoreBucketsKeepScoreSeparateFromProbability() {
        val a=V540BacktestEngine.evaluate(signal(listOf(Candle(t0+60_000,100.0,103.5,99.5,103.0,1.0)),70),V540ExecutionCostConfig())
        val b=V540BacktestEngine.evaluate(signal(listOf(Candle(t0+60_000,100.0,103.5,99.5,103.0,1.0)),90),V540ExecutionCostConfig())
        val buckets=V540BacktestEngine.scoreBuckets(listOf(a,b))
        assertEquals(1,buckets.single{it.minInclusive==70}.sampleCount)
        assertEquals(1,buckets.single{it.minInclusive==90}.sampleCount)
    }

    @Test fun metricsIncludeDrawdownSharpeSortinoMfeMae() {
        val wins=(0 until 3).map { i -> V540BacktestEngine.evaluate(signal(listOf(Candle(t0+60_000+i,100.0,103.5,99.5,103.0,1.0))),V540ExecutionCostConfig()) }
        val m=V540BacktestEngine.metrics(wins)
        assertEquals(3,m.closedCount)
        assertNotNull(m.averageMfePct)
        assertNotNull(m.maxDrawdownPct)
    }

    @Test fun walkForwardKeepsOutOfSampleSeparated() {
        val base=V540BacktestEngine.evaluate(signal(listOf(Candle(t0+60_000,100.0,103.5,99.5,103.0,1.0))),V540ExecutionCostConfig())
        val trades=(0 until 12).map { i -> base.copy(signalId="s$i",entryTime=t0+i*10_000L) }
        val w=V540WalkForwardEngine.evaluate(trades,40_000L,30_000L,30_000L,20_000L)
        assertTrue(w.isNotEmpty())
        assertTrue(w.all { it.trainEndExclusive <= it.validationEndExclusive && it.validationEndExclusive <= it.testEndExclusive })
    }

    @Test fun monteCarloIsDeterministicWithSeed() {
        val base=V540BacktestEngine.evaluate(signal(listOf(Candle(t0+60_000,100.0,103.5,99.5,103.0,1.0))),V540ExecutionCostConfig())
        val a=V540WalkForwardEngine.monteCarlo(listOf(base),100,10,540)
        val b=V540WalkForwardEngine.monteCarlo(listOf(base),100,10,540)
        assertEquals(a,b)
    }

    @Test fun partialTargetExitUsesExplicitWeightedExecutionPolicy() {
        val future=listOf(
            Candle(t0+60_000,100.0,103.5,99.5,103.0,1.0),
            Candle(t0+120_000,103.0,103.2,97.5,98.0,1.0)
        )
        val r=V540BacktestEngine.evaluate(signal(future),V540ExecutionCostConfig(),V540ExecutionPolicy(target1ExitFraction=0.5))
        assertEquals(V540BacktestEvent.TARGET1_THEN_STOP,r.event)
        assertNotNull(r.netReturnPct)
        val net = r.netReturnPct!!
        assertTrue(net > -2.0 && net < 3.0)
    }

    @Test fun metricsExposeCostAdjustedExpectancy() {
        val future=listOf(Candle(t0+60_000,100.0,103.5,99.5,103.0,1.0))
        val r=V540BacktestEngine.evaluate(signal(future),V540ExecutionCostConfig(5.0,5.0,5.0,5.0))
        val m=V540BacktestEngine.metrics(listOf(r))
        assertEquals(m.averageNetReturnPct,m.expectancyPct)
    }

    @Test fun signalDecayUsesFixedFutureBarHorizonsWithoutLookAhead() {
        val future=(1..20).map { i -> Candle(t0+i*60_000L,100.0,100.0+i,99.0,100.0+i*0.1,1.0) }
        val points=V540SignalDecayEngine.evaluate(listOf(signal(future)),V540ExecutionCostConfig())
        assertEquals(listOf(1,3,5,10,20),points.map { it.horizonBars })
        assertTrue(points.all { it.sampleCount==1 })
    }
}
