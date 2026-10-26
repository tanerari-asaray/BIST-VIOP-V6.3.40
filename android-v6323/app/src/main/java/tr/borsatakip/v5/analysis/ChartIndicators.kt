package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.Candle
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Aligned, causal display calculations. Insufficient history remains null, never zero. */
object ChartIndicators {
    data class Series(
        val ema9: List<Double?>, val ema21: List<Double?>,
        val sma50: List<Double?>, val sma200: List<Double?>,
        val vwap: List<Double?>, val upper: List<Double?>, val lower: List<Double?>,
        val rsi: List<Double?>, val macd: List<Double?>, val signal: List<Double?>,
        val histogram: List<Double?>, val atr: List<Double?>, val volume20: List<Double?>
    )

    fun calculate(bars: List<Candle>, intraday: Boolean = true): Series {
        val closes = bars.map { it.close }
        val fast = ema(closes, 12); val slow = ema(closes, 26)
        val macd = List(bars.size) { i -> fast[i]?.let { f -> slow[i]?.let { f - it } } }
        val first = macd.indexOfFirst { it != null }
        val signal = MutableList<Double?>(bars.size) { null }
        if (first >= 0) ema(macd.drop(first).map { requireNotNull(it) }, 9).forEachIndexed { i, v -> signal[first + i] = v }
        val upper = MutableList<Double?>(bars.size) { null }; val lower = upper.toMutableList()
        for (i in 19 until bars.size) {
            val w = closes.subList(i - 19, i + 1); val mean = w.average()
            val sd = sqrt(w.sumOf { (it - mean) * (it - mean) } / 20)
            upper[i] = mean + 2 * sd; lower[i] = mean - 2 * sd
        }
        val tr = bars.mapIndexed { i, b ->
            if (i == 0) b.high - b.low else max(b.high - b.low, max(abs(b.high - bars[i - 1].close), abs(b.low - bars[i - 1].close)))
        }
        val atr = MutableList<Double?>(bars.size) { null }
        // Require a previous close for every true range in the initial Wilder seed.
        if (bars.size >= 15) {
            var a = tr.subList(1, 15).average(); atr[14] = a
            for (i in 15 until bars.size) { a = (a * 13 + tr[i]) / 14; atr[i] = a }
        }
        return Series(ema(closes, 9), ema(closes, 21), sma(closes, 50), sma(closes, 200),
            sessionVwap(bars, intraday), upper, lower, rsi(closes), macd, signal,
            List(bars.size) { i -> macd[i]?.let { m -> signal[i]?.let { m - it } } }, atr, sma(bars.map { it.volume }, 20))
    }

    fun sma(values: List<Double>, period: Int): List<Double?> {
        val result = MutableList<Double?>(values.size) { null }
        if (period <= 0) return result
        var sum = 0.0
        values.forEachIndexed { i, v ->
            sum += v
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) result[i] = sum / period
        }
        return result
    }

    fun ema(values: List<Double>, period: Int): List<Double?> {
        val result = MutableList<Double?>(values.size) { null }
        if (period <= 0 || values.size < period) return result
        var e = values.take(period).average(); result[period - 1] = e
        val alpha = 2.0 / (period + 1)
        for (i in period until values.size) { e += alpha * (values[i] - e); result[i] = e }
        return result
    }

    fun rsi(values: List<Double>, period: Int = 14): List<Double?> {
        val result = MutableList<Double?>(values.size) { null }
        if (period <= 0 || values.size <= period) return result
        var gain = 0.0; var loss = 0.0
        for (i in 1..period) { val d = values[i] - values[i - 1]; gain += max(d, 0.0); loss += max(-d, 0.0) }
        gain /= period; loss /= period
        fun value() = when { gain == 0.0 && loss == 0.0 -> 50.0; loss == 0.0 -> 100.0; else -> 100 - 100 / (1 + gain / loss) }
        result[period] = value()
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]
            gain = (gain * (period - 1) + max(d, 0.0)) / period
            loss = (loss * (period - 1) + max(-d, 0.0)) / period
            result[i] = value()
        }
        return result
    }

    /** Session anchor is exchange-local calendar day; absent volume has no VWAP. */
    fun sessionVwap(bars: List<Candle>, intraday: Boolean): List<Double?> {
        val zone = ZoneId.of("Europe/Istanbul")
        var day: java.time.LocalDate? = null; var volume = 0.0; var weighted = 0.0
        return bars.map { b ->
            val current = Instant.ofEpochMilli(b.timestamp).atZone(zone).toLocalDate()
            if (current != day) { day = current; volume = 0.0; weighted = 0.0 }
            volume += b.volume; weighted += (b.high + b.low + b.close) / 3 * b.volume
            if (intraday && volume > 0.0) weighted / volume else null
        }
    }
}
