package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyOptimizationEngineTest {
    private fun p(
        id: StrategyProfileEngine.StrategyId,
        n: Int,
        win: Double,
        avg: Double,
        pf: Double,
        dd: Double
    ) = StrategyOptimizationEngine.Performance(id, n, win, avg, pf, dd)

    @Test fun insufficientSamplesAlwaysUseFailSafeCombinedWeights() {
        val result = StrategyOptimizationEngine.optimize(
            StrategyProfileEngine.StrategyId.entries.map { p(it, 10, 90.0, 1.0, 3.0, 2.0) }
        )
        assertFalse(result.eligible)
        assertEquals(StrategyOptimizationEngine.BASE_WEIGHTS, result.weights)
    }

    @Test fun betterVerifiedStrategyGetsMoreWeightButStepIsBounded() {
        val perf = StrategyProfileEngine.StrategyId.entries.map { id ->
            if (id == StrategyProfileEngine.StrategyId.TREND) p(id, 100, 78.0, 1.2, 2.5, 4.0)
            else p(id, 100, 52.0, 0.1, 1.1, 12.0)
        }
        val previous = StrategyOptimizationEngine.BASE_WEIGHTS
        val result = StrategyOptimizationEngine.optimize(perf, previous)
        assertTrue(result.eligible)
        assertTrue(result.weights.getValue(StrategyProfileEngine.StrategyId.TREND) > previous.getValue(StrategyProfileEngine.StrategyId.TREND))
        assertTrue(result.weights.getValue(StrategyProfileEngine.StrategyId.TREND) <= previous.getValue(StrategyProfileEngine.StrategyId.TREND) + StrategyOptimizationEngine.MAX_STEP + 1e-9)
        assertTrue(result.weights.values.all { it <= StrategyOptimizationEngine.MAX_WEIGHT + 1e-9 })
        assertEquals(1.0, result.weights.values.sum(), 0.000001)
    }


    @Test fun everyWeightRespectsStepAfterNormalizationUnderExtremeTarget() {
        val ids = StrategyProfileEngine.StrategyId.entries
        val perf = ids.map { id ->
            if (id == StrategyProfileEngine.StrategyId.TREND) p(id,100,100.0,2.0,3.0,0.0)
            else p(id,100,0.0,-2.0,0.0,25.0)
        }
        val previous = StrategyOptimizationEngine.BASE_WEIGHTS
        val result = StrategyOptimizationEngine.optimize(perf, previous)
        assertTrue(result.eligible)
        ids.forEach { id ->
            val delta = kotlin.math.abs(result.weights.getValue(id) - previous.getValue(id))
            assertTrue("$id step exceeded: $delta", delta <= StrategyOptimizationEngine.MAX_STEP + 1e-9)
        }
        assertEquals(1.0, result.weights.values.sum(), 0.000001)
    }

    @Test fun ensembleScoreUsesWeightsAndNeverInventsFutureOutcome() {
        val scores = listOf(
            StrategyProfileEngine.Score(StrategyProfileEngine.StrategyId.TREND, 90, true, ""),
            StrategyProfileEngine.Score(StrategyProfileEngine.StrategyId.MOMENTUM, 30, false, ""),
            StrategyProfileEngine.Score(StrategyProfileEngine.StrategyId.BREAKOUT, 30, false, ""),
            StrategyProfileEngine.Score(StrategyProfileEngine.StrategyId.MEAN_REVERSION, 30, false, ""),
            StrategyProfileEngine.Score(StrategyProfileEngine.StrategyId.MTF_REGIME, 30, false, ""),
            StrategyProfileEngine.Score(StrategyProfileEngine.StrategyId.COMBINED, 60, true, "")
        )
        val weights = StrategyOptimizationEngine.BASE_WEIGHTS.toMutableMap().apply {
            this[StrategyProfileEngine.StrategyId.TREND] = 0.40
            this[StrategyProfileEngine.StrategyId.COMBINED] = 0.20
            this[StrategyProfileEngine.StrategyId.MOMENTUM] = 0.10
            this[StrategyProfileEngine.StrategyId.BREAKOUT] = 0.10
            this[StrategyProfileEngine.StrategyId.MEAN_REVERSION] = 0.10
            this[StrategyProfileEngine.StrategyId.MTF_REGIME] = 0.10
        }
        val ensemble = StrategyOptimizationEngine.ensembleScore(scores, weights)
        assertEquals(60, ensemble)
    }

    @Test fun rankingAdjustmentIsHardBounded() {
        assertEquals(87, StrategyOptimizationEngine.adjustedRanking(80, 100))
        assertEquals(72, StrategyOptimizationEngine.adjustedRanking(80, 0))
        assertEquals(58, StrategyOptimizationEngine.adjustedRanking(50, 100))
    }
}
