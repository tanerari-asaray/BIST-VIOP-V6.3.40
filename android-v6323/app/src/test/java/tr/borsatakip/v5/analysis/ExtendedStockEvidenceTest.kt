package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class ExtendedStockEvidenceTest {
    private fun observations(n: Int) = (0 until n).map { i ->
        val close = 50.0 + i * 0.15 + (i % 7) * 0.25
        Candle(1_786_000_000_000L + i * 300_000L, close - 0.2, close + 0.65, close - 0.85, close, 1_000.0+i)
    }

    @Test fun indicatorsNeedRealHistory() {
        val item = ExtendedStockEvidence.indicators(observations(4), true)
        assertNull(item.macd)
        assertNull(item.rsi14)
        assertNull(item.adx14)
        assertNull(item.cci20)
    }

    @Test fun independentIndicatorsUseVerifiedCandles() {
        val indicators = ExtendedStockEvidence.indicators(observations(110), true)
        assertNotNull(indicators.rsi14)
        assertNotNull(indicators.macd)
        assertTrue(indicators.stochastic14!! in 0.0..100.0)
        assertTrue(indicators.adx14!! in 0.0..100.0)
        assertTrue(indicators.momentum10Pct!!.isFinite())
    }

    @Test fun levelsDoNotInventMissingPivots() {
        assertEquals(ExtendedStockEvidence.Ladder(emptyList(), emptyList()),
            ExtendedStockEvidence.ladder(observations(4), 50.0))
        assertEquals(ExtendedStockEvidence.Ladder(emptyList(), emptyList()),
            ExtendedStockEvidence.ladder(observations(110), Double.NaN))
    }

    @Test fun levelsAreOnTheCorrectSideOfTheReferencePrice() {
        val bars = (0 until 65).map { i ->
            val high = if (i % 6 == 2) 125.0 + i * 0.1 else 107.0
            val low = if (i % 6 == 5) 75.0 - i * 0.1 else 95.0
            Candle(1_786_000_000_000L + i * 300_000L, 101.0, high, low, 101.0, 100.0)
        }
        val levels = ExtendedStockEvidence.ladder(bars, 101.0)
        assertTrue(levels.resistances.all { it > 101.0 })
        assertTrue(levels.supports.all { it < 101.0 })
        assertTrue(levels.resistances.size <= 3 && levels.supports.size <= 3)
    }
}
