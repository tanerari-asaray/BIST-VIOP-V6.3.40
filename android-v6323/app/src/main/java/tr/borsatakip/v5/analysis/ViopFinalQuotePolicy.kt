package tr.borsatakip.v5.analysis

/**
 * Conservative data-only gate for the FINAL VİOP quote snapshot.
 * This must NEVER infer freshness from the device receipt time.
 * Gross/net entry-price viability is checked separately by ViopV6Policy.currentEntry.
 */
object ViopFinalQuotePolicy {
    const val MAX_QUOTE_AGE_MS = 60_000L
    const val MAX_FUTURE_SKEW_MS = 15_000L

    fun rejectReason(
        expectedSymbol: String,
        responseSymbol: String,
        price: Double,
        timestampMs: Long,
        nowMs: Long,
        realtime: Boolean,
        delaySeconds: Int?,
        currentSessionIncluded: Boolean
    ): String? = when {
        responseSymbol.trim().uppercase() != expectedSymbol.trim().uppercase() -> "FINAL_SYMBOL_MISMATCH"
        !price.isFinite() || price <= 0.0 -> "FINAL_INVALID_PRICE"
        timestampMs < 1_000_000_000_000L -> "FINAL_TIMESTAMP_MUST_BE_MILLISECONDS"
        timestampMs > nowMs + MAX_FUTURE_SKEW_MS -> "FINAL_FUTURE_TIMESTAMP"
        nowMs - timestampMs > MAX_QUOTE_AGE_MS -> "FINAL_STALE_DATA"
        !realtime || !currentSessionIncluded -> "FINAL_REALTIME_NOT_VERIFIED"
        delaySeconds != 0 -> "FINAL_DELAYED_OR_UNKNOWN"
        else -> null
    }
}
