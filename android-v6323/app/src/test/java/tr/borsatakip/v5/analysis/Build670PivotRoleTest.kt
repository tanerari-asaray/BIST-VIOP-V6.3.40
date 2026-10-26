package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class Build670PivotRoleTest {
    private fun bars(closes: List<Double>) = closes.mapIndexed { i, close ->
        Candle(1_700_000_000_000L + i * 300_000L, close,
            if (i == 3) 110.0 else close + 1.0, close - 1.0, close, 1000.0)
    }
    @Test fun twoClosedBreakoutBarsTurnResistanceIntoSupport() {
        val rows = bars(List(12) { 100.0 } + listOf(112.0, 113.0))
        val pivot = ExtendedStockEvidence.pivotLevels(rows).single { it.price == 110.0 }
        assertTrue(pivot.support); assertTrue(pivot.roleChanged)
        val levels = ChartSupportResistance.fromConfirmed(rows)
        assertEquals(110.0, levels.support!!.price, 0.00001)
        assertEquals(ChartSupportResistance.Evidence.ROLE_REVERSAL, levels.support!!.evidence)
    }
    @Test fun aSingleCloseOrWickCannotChangeRole() {
        val rows = bars(List(12) { 100.0 } + 112.0)
        assertFalse(ExtendedStockEvidence.pivotLevels(rows).single { it.price == 110.0 }.support)
        val wick = bars(List(14) { 100.0 }).toMutableList()
        wick[12] = wick[12].copy(high = 114.0)
        assertFalse(ExtendedStockEvidence.pivotLevels(wick).single { it.price == 110.0 }.support)
    }
    @Test fun twoClosesBackBelowFlipTheLevelBackToResistance() {
        val rows = bars(List(12) { 100.0 } + listOf(112.0, 113.0, 108.0, 107.0))
        val pivot = ExtendedStockEvidence.pivotLevels(rows).single { it.price == 110.0 }
        assertFalse(pivot.support); assertTrue(pivot.roleChanged)
        assertTrue(ExtendedStockEvidence.ladder(rows, 107.0).resistances.contains(110.0))
    }
    @Test fun flatAndTrendingRsiAndBollingerRemainAligned() {
        val flat = ChartIndicators.calculate(bars(List(30) { 100.0 }))
        assertNull(flat.rsi[13]); assertEquals(50.0, flat.rsi[14]!!, 0.00001)
        assertNull(flat.upper[18]); assertEquals(100.0, flat.upper[19]!!, 0.00001)
        assertEquals(100.0, flat.lower[19]!!, 0.00001)
        assertEquals(100.0, ChartIndicators.rsi((1..30).map(Int::toDouble)).last()!!, 0.00001)
        assertEquals(0.0, ChartIndicators.rsi((30 downTo 1).map(Int::toDouble)).last()!!, 0.00001)
    }
}
