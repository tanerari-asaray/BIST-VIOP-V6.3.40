package tr.borsatakip.v5.data

import tr.borsatakip.v5.model.Stock
import java.util.Locale

/** Keep quote price, timestamp and currency together when enriching it with daily history. */
internal object DelayedQuoteMergePolicy {
    fun merge(history: Stock, quote: Stock): Stock {
        require(history.symbol.trim().uppercase(Locale.ROOT) == quote.symbol.trim().uppercase(Locale.ROOT)) {
            "Quote/history symbol mismatch"
        }
        return history.copy(
            quotePrice = quote.quotePrice,
            previousClose = quote.previousClose ?: history.previousClose,
            dataTimestamp = quote.dataTimestamp,
            receivedAt = quote.receivedAt,
            receivedElapsedRealtime = quote.receivedElapsedRealtime,
            companyName = quote.companyName ?: history.companyName,
            currency = quote.currency,
            isRealtime = false,
            delaySeconds = null,
            bid = null,
            ask = null
        )
    }
}
