package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.TechnicalSnapshot

class MultiTimeframeSignalAnalyzerTest {
    private fun candles(start: Double, step: Double, count: Int = 60): List<Candle> = (0 until count).map { i ->
        val close = start + step * i
        Candle(i * 60_000L + 1L, close - 0.2, close + 0.5, close - 0.5, close, 1000.0 + i)
    }

    @Test fun risingSeriesIsUp() {
        val r = MultiTimeframeSignalAnalyzer.trend(candles(100.0, 0.4))
        assertEquals(MultiTimeframeSignalAnalyzer.TrendDirection.UP, r.direction)
        assertTrue((r.rSquared ?: 0.0) > 0.9)
    }

    @Test fun fallingSeriesIsDown() {
        val r = MultiTimeframeSignalAnalyzer.trend(candles(150.0, -0.35))
        assertEquals(MultiTimeframeSignalAnalyzer.TrendDirection.DOWN, r.direction)
    }

    @Test fun flatSeriesIsSideways() {
        val r = MultiTimeframeSignalAnalyzer.trend(candles(100.0, 0.0))
        assertEquals(MultiTimeframeSignalAnalyzer.TrendDirection.SIDEWAYS, r.direction)
    }

    @Test fun alignedUpFramesProduceBuySideSummary() {
        val up = MultiTimeframeSignalAnalyzer.TrendResult(MultiTimeframeSignalAnalyzer.TrendDirection.UP, 4.0, 0.85, 60)
        val frames = listOf("3 DK", "5 DK", "1 SA", "1 GÜN").map { MultiTimeframeSignalAnalyzer.TimeframeTrend(it, up) }
        val tech = TechnicalSnapshot(101.0, 100.0, 98.0, 60.0, 1.2, 0.8, 110.0, 95.0, 2.0, 100.0, 1.6, 96.0, 108.0)
        val s = MultiTimeframeSignalAnalyzer.summarize(frames, 105.0, tech)
        assertTrue(s.advice == MultiTimeframeSignalAnalyzer.Advice.STRONG_BUY || s.advice == MultiTimeframeSignalAnalyzer.Advice.BUY)
        assertTrue(s.explanation.contains("3 DK"))
    }
}
