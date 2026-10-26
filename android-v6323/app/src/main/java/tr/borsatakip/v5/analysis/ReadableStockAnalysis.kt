package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.Candle
import java.util.Locale
import java.time.Instant
import java.time.ZoneId

/**
 * Descriptive analysis only. Never authorizes orders and never assumes that the
 * newest bar has closed. No observations => no direction or price prediction.
 */
object ReadableStockAnalysis {
    enum class Direction { RISING, FALLING, MIXED }

    data class Snapshot(
        val direction: Direction,
        val period: String,
        val examinedPrice: Double,
        val support: Double,
        val resistance: Double,
        val rsi: Double?,
        val volumeRatio: Double?,
        val atrPercent: Double?,
        val sampleTime: Long,
        val priceMovement: String,
        val importantLevels: String,
        val changeConditions: String,
        val plainIndicatorNote: String
    )

    private val locale = Locale.forLanguageTag("tr-TR")
    private val istanbul = ZoneId.of("Europe/Istanbul")
    private fun format(value: Double): String = String.format(locale, "%.2f", value)

    /**
     * An intraday trend must be based on bars of the requested interval. Missing
     * same-session bars and thin, unrelated observations are not a valid trend.
     * Overnight/session gaps are allowed; they are not fabricated candles.
     */
    private fun cadenceValid(bars: List<Candle>, period: ChartTimeframe): Boolean {
        val interval = period.aggregateMinutes ?: period.requestIntervalMinutes ?: return true
        val last = bars.takeLast(30)
        if (last.size < 30) return false
        val intervalMs = interval * 60_000L
        var continuousPairs = 0
        for (i in 1 until last.size) {
            val previous = last[i - 1]
            val next = last[i]
            val firstDay = Instant.ofEpochMilli(previous.timestamp).atZone(istanbul).toLocalDate()
            val secondDay = Instant.ofEpochMilli(next.timestamp).atZone(istanbul).toLocalDate()
            if (firstDay == secondDay) {
                if (next.timestamp - previous.timestamp != intervalMs) return false
                continuousPairs++
            }
        }
        return continuousPairs >= 6
    }

    /** Recent bar may be forming: discard it, irrespective of local time and market state. */
    fun analyze(input: List<Candle>, period: ChartTimeframe): Snapshot? {
        val clean = OhlcvResampler.sanitize(input)
        if (clean.size < 31) return null
        // A malformed future-dated observation must not influence any timeframe.
        if (clean.any { it.timestamp > System.currentTimeMillis() + 60_000L }) return null
        val closedCandidates = clean.dropLast(1)
        if (!cadenceValid(closedCandidates, period)) return null
        val recent = closedCandidates.last()
        val earlier = closedCandidates.dropLast(1).takeLast(20)
        if (earlier.size != 20) return null
        val s = ChartIndicators.calculate(closedCandidates, !period.daily)
        val ema9 = s.ema9.lastOrNull() ?: return null
        val ema21 = s.ema21.lastOrNull() ?: return null
        val support = earlier.minOf { it.low }
        val resistance = earlier.maxOf { it.high }
        val direction = when {
            recent.close > ema9 && ema9 > ema21 -> Direction.RISING
            recent.close < ema9 && ema9 < ema21 -> Direction.FALLING
            else -> Direction.MIXED
        }
        val priceMovement = when (direction) {
            Direction.RISING -> "Seçilen ${period.label.lowercase(locale)} grafiğinde fiyat, kısa ve orta vadeli fiyat ortalamalarının üstünde. Bu durum incelenen mumlarda yukarı yönün daha belirgin olduğunu gösterir; sonraki fiyatı garanti etmez."
            Direction.FALLING -> "Seçilen ${period.label.lowercase(locale)} grafiğinde fiyat, kısa ve orta vadeli fiyat ortalamalarının altında. Bu durum incelenen mumlarda satış yönünün daha baskın olduğunu gösterir; düşüşün süreceği kesin değildir."
            Direction.MIXED -> "Seçilen ${period.label.lowercase(locale)} grafiğinde fiyat ve kısa vadeli ortalamalar aynı yönde değil. Bu yüzden net bir yükseliş veya düşüş demek için yeterli uyum yok."
        }
        val importantLevels = "${format(support)} seviyesi destek: fiyatın daha önce alt sınıra yaklaştığı bölge. " +
            "${format(resistance)} seviyesi direnç: fiyatın daha önce üst sınıra yaklaştığı bölge. " +
            "Bunlar önceki 20 mumdan hesaplanan izleme seviyeleridir; kesin alım veya satım noktası değildir."
        val changeConditions = "Sonraki doğrulanmış kapanışlar ${format(resistance)} üstüne çıkar ve işlem hacmi de artarsa yukarı yön güçlenebilir. " +
            "${format(support)} altına inerlerse görünüm zayıflayabilir. İki seviye arasında kalırsa yön hâlâ belirsiz olabilir."
        val rsi = s.rsi.lastOrNull()?.takeIf(Double::isFinite)
        val referenceVolume = earlier.map { it.volume }.average()
        val volumeRatio = referenceVolume.takeIf { it > 0 }?.let { recent.volume / it }?.takeIf(Double::isFinite)
        val atr = s.atr.lastOrNull()?.let { 100.0 * it / recent.close }?.takeIf(Double::isFinite)
        val indicators = listOfNotNull(
            rsi?.let { "RSI ${format(it)}: son fiyat hareketlerinin gücünü 0 ile 100 arasında özetler; tek başına işlem işareti değildir." },
            volumeRatio?.let { "Hacim ${format(it)} kat: son incelenen mumun işlem miktarını önceki 20 mumun ortalamasıyla karşılaştırır." },
            atr?.let { "ATR %${format(it)}: fiyatın son dönemlerde ortalama ne kadar dalgalandığını kabaca gösterir; hedef fiyat değildir." }
        ).joinToString("\n")
        return Snapshot(direction, period.label, recent.close, support, resistance,
            rsi, volumeRatio, atr, recent.timestamp, priceMovement, importantLevels,
            changeConditions, indicators)
    }
}
