package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Technical-only facts based on *confirmed* and sanitized candles.
 * No provider quotes, balance sheet ratios, analyst targets or trading instructions are synthesized here.
 */
object ExtendedStockEvidence {
    data class Indicators(
        val rsi14: Double?, val macd: Double?, val stochastic14: Double?,
        val cci20: Double?, val adx14: Double?, val momentum10Pct: Double?
    )
    data class Ladder(val resistances: List<Double>, val supports: List<Double>)

    fun indicators(confirmed: List<Candle>, intraday: Boolean): Indicators {
        val rows = OhlcvResampler.sanitize(confirmed)
        if (rows.isEmpty()) return Indicators(null, null, null, null, null, null)
        val base = ChartIndicators.calculate(rows, intraday)
        val close = rows.last().close
        val previous = rows.getOrNull(rows.size - 11)?.close?.takeIf { it > 0.0 }
        val stochastic = rows.takeLast(14).takeIf { it.size == 14 }?.let { w ->
            val hi = w.maxOf { it.high }; val lo = w.minOf { it.low }
            if (hi > lo) (close - lo) / (hi - lo) * 100.0 else null
        }
        val cci = rows.takeLast(20).takeIf { it.size == 20 }?.let { w ->
            val typical = w.map { (it.high + it.low + it.close) / 3.0 }
            val mean = typical.average()
            val deviation = typical.map { abs(it - mean) }.average()
            if (deviation > 0) (typical.last() - mean) / (0.015 * deviation) else null
        }
        return Indicators(base.rsi.lastOrNull()?.takeIf { it.isFinite() },
            base.macd.lastOrNull()?.takeIf { it.isFinite() },
            stochastic?.takeIf { it.isFinite() }, cci?.takeIf { it.isFinite() },
            adx(rows)?.takeIf { it.isFinite() },
            previous?.let { ((close / it) - 1.0) * 100.0 }?.takeIf { it.isFinite() })
    }

    /** Wilder's ADX(14): 14 DX values are needed after the 14-period DI seed. */
    fun adx(rows: List<Candle>, period: Int = 14): Double? {
        if (period < 2 || rows.size < period * 2 + 1) return null
        val tr = DoubleArray(rows.size)
        val posDm = DoubleArray(rows.size)
        val negDm = DoubleArray(rows.size)
        for (i in 1 until rows.size) {
            val up = rows[i].high - rows[i - 1].high
            val down = rows[i - 1].low - rows[i].low
            posDm[i] = if (up > down && up > 0) up else 0.0
            negDm[i] = if (down > up && down > 0) down else 0.0
            tr[i] = max(rows[i].high - rows[i].low, max(abs(rows[i].high - rows[i-1].close), abs(rows[i].low - rows[i-1].close)))
        }
        var smoothTr = (1..period).sumOf { tr[it] }
        var smoothPos = (1..period).sumOf { posDm[it] }
        var smoothNeg = (1..period).sumOf { negDm[it] }
        fun dx(): Double {
            if (smoothTr <= 0) return 0.0
            val diPos = 100.0 * smoothPos / smoothTr
            val diNeg = 100.0 * smoothNeg / smoothTr
            return if (diPos + diNeg > 0) 100.0 * abs(diPos - diNeg) / (diPos + diNeg) else 0.0
        }
        val dxValues = mutableListOf(dx())
        for (i in period+1 until rows.size) {
            smoothTr = smoothTr - smoothTr / period + tr[i]
            smoothPos = smoothPos - smoothPos / period + posDm[i]
            smoothNeg = smoothNeg - smoothNeg / period + negDm[i]
            dxValues.add(dx())
        }
        if (dxValues.size < period) return null
        var value = dxValues.take(period).average()
        for (i in period until dxValues.size) value = (value * (period-1) + dxValues[i]) / period
        return value.takeIf { it.isFinite() && it in 0.0..100.0 }
    }

    data class PivotLevel(val price: Double, val support: Boolean, val roleChanged: Boolean)

    /** Two bars on each side confirm the pivot. Two closes beyond a 0.1% buffer
     * confirm a role change; a wick or a single close cannot flip the level. */
    fun pivotLevels(confirmed: List<Candle>): List<PivotLevel> {
        val rows = OhlcvResampler.sanitize(confirmed)
        val levels = mutableListOf<PivotLevel>()
        fun follow(index: Int, price: Double, initialSupport: Boolean): PivotLevel {
            var support = initialSupport
            var changed = false
            var consecutive = 0
            for (j in index + 3 until rows.size) {
                val crossed = if (support) rows[j].close < price * 0.999
                    else rows[j].close > price * 1.001
                consecutive = if (crossed) consecutive + 1 else 0
                if (consecutive == 2) {
                    support = !support; changed = true; consecutive = 0
                }
            }
            return PivotLevel(price, support, changed)
        }
        for (i in 2 until rows.size - 2) {
            if ((i-2..i+2).all { it == i || rows[i].high > rows[it].high })
                levels += follow(i, rows[i].high, false)
            if ((i-2..i+2).all { it == i || rows[i].low < rows[it].low })
                levels += follow(i, rows[i].low, true)
        }
        return levels
    }

    /** Nearest valid levels, using the same breakout/role policy as the chart. */
    fun ladder(confirmed: List<Candle>, referencePrice: Double): Ladder {
        if (!referencePrice.isFinite() || referencePrice <= 0.0) return Ladder(emptyList(), emptyList())
        val levels = pivotLevels(confirmed)
        fun distinctLevels(support: Boolean): List<Double> {
            val values = levels.filter { it.support == support }.map { it.price }
            val ordered = if (support) values.filter { it < referencePrice }.sortedDescending()
                else values.filter { it > referencePrice }.sorted()
            val result = mutableListOf<Double>()
            for (value in ordered) {
                if (result.none { abs(it - value) / referencePrice < 0.003 }) result.add(value)
                if (result.size == 3) break
            }
            return result
        }
        return Ladder(distinctLevels(false), distinctLevels(true))
    }
}
