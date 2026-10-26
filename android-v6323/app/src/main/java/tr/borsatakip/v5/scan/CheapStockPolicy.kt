package tr.borsatakip.v5.scan

/** Nominal price screen, NOT a fundamental valuation model or an order recommendation. */
object CheapStockPolicy {
    const val DEFAULT_MAX_TRY = 50.0
    const val MAX_QUOTE_AGE_MS = 7L * 24L * 60L * 60L * 1000L

    enum class Reject { PRICE_INVALID, CURRENCY_UNKNOWN, STALE_OR_INVALID_TIME, ABOVE_LIMIT }
    enum class PriceKind { QUOTE, LAST_CANDLE_CLOSE }

    data class Match(val price: Double, val kind: PriceKind)
    sealed class Verdict {
        data class Accepted(val match: Match) : Verdict()
        data class Rejected(val reason: Reject) : Verdict()
    }

    fun evaluate(
        quotePrice: Double?, lastCandleClose: Double?, currency: String?,
        timestampMs: Long, maxPriceTry: Double, nowMs: Long
    ): Verdict {
        require(maxPriceTry.isFinite() && maxPriceTry > 0.0) { "Geçersiz fiyat limiti" }
        val currencyCode = currency?.trim()?.uppercase(java.util.Locale.ROOT)
        if (currencyCode !in setOf("TRY", "TL", "₺")) return Verdict.Rejected(Reject.CURRENCY_UNKNOWN)
        if (timestampMs <= 0L || timestampMs > nowMs + 120_000L || nowMs - timestampMs > MAX_QUOTE_AGE_MS) {
            return Verdict.Rejected(Reject.STALE_OR_INVALID_TIME)
        }
        val quote = quotePrice?.takeIf { it.isFinite() && it > 0.0 }
        val close = lastCandleClose?.takeIf { it.isFinite() && it > 0.0 }
        val price = quote ?: close ?: return Verdict.Rejected(Reject.PRICE_INVALID)
        if (price > maxPriceTry) return Verdict.Rejected(Reject.ABOVE_LIMIT)
        return Verdict.Accepted(Match(price, if (quote != null) PriceKind.QUOTE else PriceKind.LAST_CANDLE_CLOSE))
    }
}