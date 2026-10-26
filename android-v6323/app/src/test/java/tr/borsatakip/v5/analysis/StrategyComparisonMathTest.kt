package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyComparisonMathTest {
    @Test fun profitFactorUsesOnlyRealizedReturns() {
        val pf = StrategyComparisonMath.profitFactor(listOf(10.0, -5.0, 5.0, -5.0))!!
        assertEquals(1.5, pf, 0.0001)
    }

    @Test fun maxDrawdownUsesCompoundedEquity() {
        val dd = StrategyComparisonMath.maxDrawdownPct(listOf(10.0, -10.0, -10.0, 5.0))!!
        assertTrue(dd > 18.0 && dd < 20.0)
    }

    @Test fun smallSamplesAreNeverPresentedAsHighConfidence() {
        assertEquals("YETERSİZ ÖRNEK", StrategyComparisonMath.confidenceLabel(19))
        assertEquals("SINIRLI", StrategyComparisonMath.confidenceLabel(20))
        assertEquals("ORTA", StrategyComparisonMath.confidenceLabel(50))
        assertEquals("YÜKSEK", StrategyComparisonMath.confidenceLabel(100))
    }
}
