package tr.borsatakip.v5.data

import tr.borsatakip.v5.model.Candle
import kotlin.math.abs
import kotlin.math.max

/** Display-only calculations. Missing samples remain null; no score or order authorization. */
object ObservationAnalysis {
    data class Metrics(val sma10: Double?, val sma20: Double?, val rsi14: Double?,
        val atr14: Double?, val volumeRatio: Double?, val support: Double?, val resistance: Double?)
    fun clean(raw: List<Candle>): List<Candle> = raw.filter {
        it.timestamp > 0 && listOf(it.open,it.high,it.low,it.close,it.volume).all(Double::isFinite) &&
            it.low > 0 && it.high >= max(it.open,it.close) && it.low <= minOf(it.open,it.close) && it.volume >= 0
    }.distinctBy { it.timestamp }.sortedBy { it.timestamp }
    fun calculate(raw: List<Candle>): Metrics {
        val bars = clean(raw)
        fun sma(n: Int) = if (bars.size >= n) bars.takeLast(n).map { it.close }.average() else null
        var rsi: Double? = null
        var atr: Double? = null
        if (bars.size >= 15) {
            val changes = bars.zipWithNext { a,b -> b.close-a.close }
            var gain = changes.take(14).sumOf { max(it,0.0) }/14
            var loss = changes.take(14).sumOf { max(-it,0.0) }/14
            changes.drop(14).forEach { gain=(gain*13+max(it,0.0))/14; loss=(loss*13+max(-it,0.0))/14 }
            rsi = when { gain==0.0 && loss==0.0 -> 50.0; loss==0.0 -> 100.0; else -> 100-100/(1+gain/loss) }
            val ranges=bars.zipWithNext { a,b -> max(b.high-b.low,max(abs(b.high-a.close),abs(b.low-a.close))) }
            var value=ranges.take(14).average()
            ranges.drop(14).forEach { value=(value*13+it)/14 }
            atr=value
        }
        val previous = bars.dropLast(1).takeLast(20)
        val averageVolume=previous.takeIf { it.size==20 }?.map { it.volume }?.average()
        return Metrics(sma(10),sma(20),rsi,atr,
            averageVolume?.takeIf { it>0 }?.let { bars.last().volume/it },
            previous.takeIf { it.size==20 }?.minOf { it.low },
            previous.takeIf { it.size==20 }?.maxOf { it.high })
    }
    fun trendLabel(t: FullUniversePolicy.Trend) = when(t) {
        FullUniversePolicy.Trend.UP -> "Yükseliş"; FullUniversePolicy.Trend.DOWN -> "Düşüş"
        FullUniversePolicy.Trend.FLAT -> "Yatay"; FullUniversePolicy.Trend.UNKNOWN -> "Yetersiz veri"
    }
}
