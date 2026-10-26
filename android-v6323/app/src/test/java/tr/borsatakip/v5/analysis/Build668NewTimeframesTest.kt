package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import java.time.Instant

/** Strict test: derived candles require real source OHLCV, no gap-filling. */
class Build668NewTimeframesTest {
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun bar(ts: Long, p: Double, v: Double = 100.0) =
        Candle(ts, p, p + 0.5, p - 0.5, p + 0.2, v)

    @Test fun thirtyMinutesOnlyFromSixContiguousFiveMinuteBars() {
        val start = t("2026-10-20T07:00:00Z") // 10:00 Europe/Istanbul
        val six = (0..5).map { i -> bar(start + i * 300_000L, 20.0 + i) }
        val aggregated = OhlcvResampler.aggregate(six, 5, 30)
        assertEquals(1, aggregated.size)
        assertEquals(20.0, aggregated.single().open, 0.0)
        assertEquals(25.2, aggregated.single().close, 0.0)
        assertEquals(25.5, aggregated.single().high, 0.0)
        assertEquals(19.5, aggregated.single().low, 0.0)
        assertEquals(600.0, aggregated.single().volume, 0.0)
        assertTrue(OhlcvResampler.aggregate(six.drop(2), 5, 30).isEmpty())
        assertTrue(OhlcvResampler.aggregate(six + six.takeLast(1), 5, 30).size <= 1)
    }

    @Test fun monthlyAggregatesRealDailyBarsWithoutInventingMissingDays() {
        val jan1 = bar(t("2026-01-02T09:00:00Z"), 10.0, 120.0)
        val jan2 = bar(t("2026-01-30T09:00:00Z"), 11.0, 180.0)
        val feb1 = bar(t("2026-02-02T09:00:00Z"), 12.0, 90.0)
        val res = OhlcvResampler.monthly(listOf(feb1, jan2, jan1))
        assertEquals(2, res.size)
        assertEquals(10.0, res[0].open, 0.0)
        assertEquals(11.2, res[0].close, 0.0)
        assertEquals(300.0, res[0].volume, 0.0)
        assertEquals(12.0, res[1].open, 0.0)
        assertEquals(90.0, res[1].volume, 0.0)
    }

    @Test fun invalidMonthlyBarsAreRejectedNotConvertedIntoPrices() {
        val at = t("2026-05-05T09:00:00Z")
        val invalid = Candle(at, 10.0, 11.0, 9.0, 10.5, -2.0)
        assertTrue(OhlcvResampler.monthly(listOf(invalid)).isEmpty())
        assertTrue(OhlcvResampler.aggregate(listOf(invalid), 5, 30).isEmpty())
    }
}
