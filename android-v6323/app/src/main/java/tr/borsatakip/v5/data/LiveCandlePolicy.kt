package tr.borsatakip.v5.data

import tr.borsatakip.v5.model.Candle

/** Live bars use opening timestamps; allow one interval plus the quote freshness budget. */
object LiveCandlePolicy {
    fun accepts(c: Candle, interval: String, requested: String, now: Long): Boolean {
        if (interval != requested || !interval.matches(Regex("[1-9][0-9]{0,2}m"))) return false
        val minutes = interval.dropLast(1).toLongOrNull() ?: return false
        if (c.timestamp <= 0L || c.timestamp > now + 15_000L || now - c.timestamp > minutes * 60_000L + 60_000L) return false
        return listOf(c.open, c.high, c.low, c.close, c.volume).all { it.isFinite() } &&
            c.low > 0.0 && c.high >= c.low && c.open in c.low..c.high && c.close in c.low..c.high && c.volume >= 0.0
    }
}
