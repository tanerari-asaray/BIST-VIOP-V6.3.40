package tr.borsatakip.v5.scan

import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.analysis.TrendUiState
import kotlin.math.abs
import kotlin.math.roundToInt

/** Technical observation of nominally low-priced stocks; not a recommendation. */
object CheapStockTrendPolicy {
    data class Assessment(val state: TrendUiState, val label: String, val strength: Int, val reason: String)

    fun assess(candles: List<Candle>): Assessment {
        // Invalid/duplicate candles must never be used to manufacture a trend.
        val valid = candles.filter {
            it.timestamp > 0L && it.open.isFinite() && it.high.isFinite() && it.low.isFinite() &&
                it.close.isFinite() && it.close > 0.0 && it.low > 0.0 &&
                it.high >= maxOf(it.open, it.close, it.low) &&
                it.low <= minOf(it.open, it.close) && it.volume.isFinite() && it.volume >= 0.0
        }.distinctBy { it.timestamp }.sortedBy { it.timestamp }
        if (valid.size < 55) return Assessment(TrendUiState.INSUFFICIENT_DATA, "VERİ YETERSİZ", 0, "Günlük OHLCV: ${valid.size}/55 geçerli mum")
        val closes = valid.map { it.close }
        fun ema(period: Int): Double {
            val k = 2.0 / (period + 1)
            var value = closes.take(period).average()
            for (i in period until closes.size) value += k * (closes[i] - value)
            return value
        }
        val fast = ema(20)
        val slow = ema(50)
        val last = closes.last()
        if (fast <= 0 || slow <= 0 || !fast.isFinite() || !slow.isFinite()) {
            return Assessment(TrendUiState.INSUFFICIENT_DATA, "VERİ GEÇERSİZ", 0, "EMA hesaplanamadı")
        }
        val rising = last > fast && fast > slow
        val falling = last < fast && fast < slow
        if (!rising && !falling) return Assessment(TrendUiState.NEUTRAL, "YATAY / KARARSIZ", 0, "EMA dizilimi yön teyidi vermiyor; güç puanı uygulanmaz")
        val displacement = abs(last / fast - 1.0) * 100.0
        val separation = abs(fast / slow - 1.0) * 100.0
        // Bounded smooth scoring; prevents large price spikes from trivially saturating at 100.
        val raw = displacement * 0.7 + separation * 1.3
        val strength = (100.0 * raw / (raw + 12.0)).roundToInt().coerceIn(1, 99)
        val strong = strength >= 60
        val state = when {
            rising && strong -> TrendUiState.STRONG_BULLISH
            rising -> TrendUiState.BULLISH
            falling && strong -> TrendUiState.STRONG_BEARISH
            else -> TrendUiState.BEARISH
        }
        val label = when (state) {
            TrendUiState.STRONG_BULLISH -> "GÜÇLÜ YÜKSELİŞ"
            TrendUiState.BULLISH -> "YÜKSELİŞ"
            TrendUiState.STRONG_BEARISH -> "GÜÇLÜ DÜŞÜŞ"
            else -> "DÜŞÜŞ"
        }
        return Assessment(state, label, strength, "EMA20/EMA50 yön teyidi • teknik izleme, AL/SAT değil")
    }
}
