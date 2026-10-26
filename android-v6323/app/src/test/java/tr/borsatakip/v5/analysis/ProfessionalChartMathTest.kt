package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import java.time.Instant

class ProfessionalChartMathTest {
    private fun bar(time: Long, price: Double, volume: Double = 100.0) = Candle(time, price, price + 1, (price - 1).coerceAtLeast(0.01), price, volume)
    private fun time(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun linearPricesHaveExactMovingAveragesAndWilderValues() {
        val bars = (1..240).map { bar(1_700_000_000_000L + it * 60_000, it.toDouble()) }
        val s = ChartIndicators.calculate(bars)
        assertNull(s.ema9[7]); assertEquals(5.0, s.ema9[8]!!, 1e-9)
        assertEquals(236.0, s.ema9.last()!!, 1e-9)
        assertEquals(230.0, s.ema21.last()!!, 1e-9)
        assertEquals(215.5, s.sma50.last()!!, 1e-9)
        assertEquals(140.5, s.sma200.last()!!, 1e-9)
        assertEquals(100.0, s.rsi.last()!!, 1e-9)
        assertEquals(2.0, s.atr.last()!!, 1e-9)
        assertEquals(7.0, s.macd.last()!!, 1e-9)
        assertEquals(0.0, s.histogram.last()!!, 1e-9)
        assertNull(s.signal[32]); assertNotNull(s.signal[33])
    }

    @Test fun missingHistoryAndZeroVolumeNeverBecomeFakeIndicatorValues() {
        val short = ChartIndicators.calculate(listOf(bar(1L, 10.0, 0.0)))
        assertNull(short.ema9.single()); assertNull(short.sma200.single())
        assertNull(short.rsi.single()); assertNull(short.atr.single()); assertNull(short.vwap.single())
        val flat = ChartIndicators.calculate((0..30).map { bar(1L + it * 60_000, 10.0, 0.0) })
        assertEquals(50.0, flat.rsi.last()!!, 0.0)
        assertTrue(flat.vwap.all { it == null })
    }

    @Test fun sessionVwapUsesVolumeAndIstanbulMidnightAndIsHiddenOnDaily() {
        val bars = listOf(bar(time("2026-10-14T20:40:00Z"), 10.0, 100.0),
            bar(time("2026-10-14T20:50:00Z"), 20.0, 300.0),
            bar(time("2026-10-14T21:00:00Z"), 30.0, 100.0))
        assertEquals(listOf(10.0, 17.5, 30.0), ChartIndicators.sessionVwap(bars, true))
        assertTrue(ChartIndicators.sessionVwap(bars, false).all { it == null })
    }

    @Test fun weeklyBucketsUseExchangeMondayAndPreserveRealOhlcv() {
        val bars = listOf(bar(time("2026-10-16T07:00:00Z"), 10.0),
            bar(time("2026-10-18T22:00:00Z"), 12.0), bar(time("2026-10-20T07:00:00Z"), 14.0))
        val weekly = OhlcvResampler.weekly(bars)
        assertEquals(2, weekly.size)
        assertEquals(12.0, weekly[1].open, 0.0); assertEquals(14.0, weekly[1].close, 0.0)
        assertEquals(15.0, weekly[1].high, 0.0); assertEquals(11.0, weekly[1].low, 0.0)
        assertEquals(200.0, weekly[1].volume, 0.0)
    }

    @Test fun fourHourBarsRejectMissingHoursAndDoNotJoinDifferentDays() {
        val t = time("2026-10-14T07:00:00Z")
        val complete = (0..3).map { bar(t + it * 3_600_000L, 10.0 + it) }
        val next = (0..2).map { bar(t + 86_400_000L + it * 3_600_000L, 20.0 + it) }
        val result = OhlcvResampler.sessionAggregate(complete + next, 60, 240)
        assertEquals(1, result.size)
        assertEquals(10.0, result.single().open, 0.0); assertEquals(13.0, result.single().close, 0.0)
        assertEquals(400.0, result.single().volume, 0.0)
        assertTrue(OhlcvResampler.sessionAggregate(complete.filterIndexed { i, _ -> i != 1 }, 60, 240).isEmpty())
    }

    @Test fun indicatorsDoNotReadFutureCandles() {
        val bars = (1..240).map { bar(1_700_000_000_000L + it * 60_000, it.toDouble()) }
        val prefix = ChartIndicators.calculate(bars.take(210))
        val full = ChartIndicators.calculate(bars)
        assertEquals(prefix.ema21, full.ema21.take(210)); assertEquals(prefix.rsi, full.rsi.take(210))
        assertEquals(prefix.atr, full.atr.take(210)); assertEquals(prefix.histogram, full.histogram.take(210))
    }
}
