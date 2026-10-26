package tr.borsatakip.v5.data

import tr.borsatakip.v5.model.DisplayQuoteSnapshot
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.Stock

/** Presentation quotes never change the closed-bar decision price or its indicators. */
object StockQuotePolicy {
    const val MAX_AGE_MS = 60_000L

    fun from(stock: Stock): DisplayQuoteSnapshot? {
        val price = stock.quotePrice?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (stock.exchangeTimestamp < 1_000_000_000_000L || stock.exchangeTimestamp > System.currentTimeMillis() + 60_000L || stock.source.isBlank()) return null
        return DisplayQuoteSnapshot(price, stock.previousClose?.takeIf { it.isFinite() && it > 0 },
            stock.exchangeTimestamp, stock.source, stock.isRealtime, stock.delaySeconds, stock.currentSessionIncluded)
    }

    fun live(q: DisplayQuoteSnapshot?, now: Long = System.currentTimeMillis()): Boolean = q != null &&
        q.realtime && q.currentSessionIncluded && q.delaySeconds != null && q.delaySeconds in 0..5 &&
        now - q.timestamp in 0L..MAX_AGE_MS

    fun change(q: DisplayQuoteSnapshot?): Double? = q?.previousDailyClose?.takeIf { it.isFinite() && it > 0 }?.let {
        ((q.price / it - 1) * 100).takeIf(Double::isFinite)
    } ?: q?.reportedDailyChangePct?.takeIf(Double::isFinite)

    fun price(item: Opportunity): Double = item.displayQuote?.price ?: item.price
    fun timestamp(item: Opportunity): Long = item.displayQuote?.timestamp
        ?: item.candles.lastOrNull()?.timestamp ?: item.exchangeTimestamp
    fun change(item: Opportunity): Double? = if (item.displayQuote != null) change(item.displayQuote) else item.dailyChangePct

    fun failureCode(error: Throwable): String {
        if (error is BackendHttpException) {
            val evidence = error.evidence
            if (BackendHttpDiagnostic.allowedCode(evidence.providerCode)) return evidence.providerCode
            return when (evidence.httpStatus) {
                401, 403 -> "AUTH_ERROR"
                429 -> "RATE_LIMIT"
                in 100..599 -> "HTTP_${evidence.httpStatus}"
                else -> "HTTP_ERROR"
            }
        }
        val message = error.message.orEmpty()
        return when {
            Regex("\\b401\\b|\\b403\\b").containsMatchIn(message) -> "AUTH_ERROR"
            message.contains("429") -> "RATE_LIMIT"
            error is java.net.SocketTimeoutException || message.contains("timeout", true) || message.contains("zaman aş", true) -> "TIMEOUT"
            error is org.json.JSONException || error is IllegalArgumentException -> "SCHEMA_ERROR"
            else -> "NETWORK_OR_PROVIDER_ERROR"
        }
    }
}
