package tr.borsatakip.v5.data

/** Pair a meta quote with its OWN market time; otherwise use a complete candle pair. */
object YahooQuotePairPolicy {
    data class Pair(val price: Double, val timestamp: Long)
    fun select(metaPrice: Double?, metaTimeMs: Long?, close: Double?, candleTimeMs: Long?,
               now: Long = System.currentTimeMillis()): Pair? {
        fun valid(price: Double?, time: Long?): Boolean = price != null && price.isFinite() && price > 0 &&
            time != null && time >= 1_000_000_000_000L && time <= now + 60_000
        return when {
            valid(metaPrice, metaTimeMs) -> Pair(requireNotNull(metaPrice), requireNotNull(metaTimeMs))
            valid(close, candleTimeMs) -> Pair(requireNotNull(close), requireNotNull(candleTimeMs))
            else -> null
        }
    }
}
