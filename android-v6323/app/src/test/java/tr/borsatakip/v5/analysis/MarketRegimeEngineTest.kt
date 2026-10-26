package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class MarketRegimeEngineTest {
    @Test fun insufficientBenchmarkNeverInventsRegime() {
        val r = MarketRegimeEngine.evaluate("XU100", candles(20, 100.0, 0.1))
        assertEquals(MarketRegimeEngine.Regime.UNKNOWN, r.regime)
        assertEquals(0, r.confidence)
    }

    @Test fun orderedUptrendProducesTrendUp() {
        val r = MarketRegimeEngine.evaluate("XU100", candles(100, 100.0, 0.35))
        assertEquals(MarketRegimeEngine.Regime.TREND_UP, r.regime)
        assertTrue(r.confidence > 0)
        assertTrue(MarketRegimeEngine.rankingAdjustment("LONG", r) > 0)
        assertTrue(MarketRegimeEngine.rankingAdjustment("SHORT", r) < 0)
    }

    private fun candles(n:Int,start:Double,step:Double)=List(n){ i ->
        val c=start+i*step
        Candle(1_700_000_000_000L+i*86_400_000L,c-0.1,c+0.3,c-0.3,c,100_000.0+i)
    }
}
