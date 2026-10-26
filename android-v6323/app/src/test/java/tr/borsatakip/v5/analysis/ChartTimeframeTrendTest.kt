package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class ChartTimeframeTrendTest {
    private fun c(i: Int, close: Double, volume: Double = 1000.0, spread: Double = 1.0): Candle =
        Candle(
            timestamp = 1_699_999_920_000L + i * 60_000L,
            open = close - 0.2,
            high = close + spread,
            low = close - spread,
            close = close,
            volume = volume
        )

    @Test fun timeframe_contract_is_exact() {
        assertEquals("1 DK", ChartTimeframe.ONE_MIN.label)
        assertEquals(1, ChartTimeframe.ONE_MIN.requestIntervalMinutes)
        assertEquals(1, ChartTimeframe.THREE_MIN.requestIntervalMinutes)
        assertEquals(3, ChartTimeframe.THREE_MIN.aggregateMinutes)
        assertEquals(5, ChartTimeframe.FIVE_MIN.requestIntervalMinutes)
        assertEquals("15 DK", ChartTimeframe.FIFTEEN_MIN.label)
        assertEquals(5, ChartTimeframe.FIFTEEN_MIN.requestIntervalMinutes)
        assertEquals(15, ChartTimeframe.FIFTEEN_MIN.aggregateMinutes)
        assertEquals(60, ChartTimeframe.ONE_HOUR.requestIntervalMinutes)
        assertTrue(ChartTimeframe.ONE_DAY.daily)
        assertTrue(ChartTimeframe.ALL_TIME.maximumRange)
    }

    @Test fun three_minute_resample_uses_real_ohlcv_aggregation() {
        val base = (0 until 6).map { i -> c(i, 100.0 + i, volume = 10.0 + i) }
        val out = OhlcvResampler.aggregate(base, 1, 3)
        assertEquals(2, out.size)
        assertEquals(base[0].open, out[0].open, 0.0001)
        assertEquals(base[2].close, out[0].close, 0.0001)
        assertEquals(base.take(3).maxOf { it.high }, out[0].high, 0.0001)
        assertEquals(base.take(3).minOf { it.low }, out[0].low, 0.0001)
        assertEquals(base.take(3).sumOf { it.volume }, out[0].volume, 0.0001)
    }

    @Test fun main_linear_regression_detects_uptrend_and_r2() {
        val candles = (0 until 60).map { i -> c(i, 100.0 + i * 0.5 + if (i % 2 == 0) 0.1 else -0.1) }
        val r = LinearTrendAnalyzer.analyze(candles)
        assertNotNull(r.main)
        assertEquals(LinearTrendAnalyzer.Direction.UP, r.main!!.direction)
        assertTrue(r.main!!.slope > 0.0)
        assertTrue(r.main!!.r2 > 0.95)
    }

    @Test fun flat_series_is_not_forced_up_or_down() {
        val candles = (0 until 40).map { i -> c(i, 100.0) }
        val r = LinearTrendAnalyzer.analyze(candles)
        assertNotNull(r.main)
        assertEquals(LinearTrendAnalyzer.Direction.FLAT, r.main!!.direction)
    }

    @Test fun invalid_rows_are_filtered_without_crash() {
        val valid = (0 until 20).map { i -> c(i, 100.0 + i * 0.1) }
        val invalid = Candle(0L, Double.NaN, 0.0, 0.0, 0.0, -1.0)
        val r = LinearTrendAnalyzer.analyze(listOf(invalid) + valid)
        assertEquals(20, r.candleCount)
        assertNotNull(r.main)
    }

    @Test fun insufficient_data_returns_message_instead_of_nan() {
        val r = LinearTrendAnalyzer.analyze((0 until 5).map { i -> c(i, 100.0 + i) })
        assertNull(r.main)
        assertEquals("Trend analizi için yeterli veri yok.", r.message)
    }
}
