package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock
import tr.borsatakip.v5.scan.CheapStockPolicy

class DelayedQuoteMergePolicyTest {
    private val now = 1_800_000_000_000L
    private fun stock(currency: String?, price: Double, candles: List<Candle> = emptyList()) = Stock(
        symbol = "TEST", companyName = null, candles = candles, source = "Yahoo delayed",
        dataTimestamp = now, isRealtime = false, receivedAt = now,
        receivedElapsedRealtime = 10L, quotePrice = price, currency = currency
    )

    @Test fun historyEnrichmentDoesNotDropQuoteCurrencyOrPrice() {
        val candles = listOf(Candle(now - 86_400_000L, 9.0, 11.0, 8.0, 10.0, 100.0))
        val merged = DelayedQuoteMergePolicy.merge(stock(null, 10.0, candles), stock("TRY", 12.0))
        assertEquals("TRY", merged.currency)
        assertEquals(candles, merged.candles)
        val verdict = CheapStockPolicy.evaluate(merged.quotePrice, merged.candles.last().close,
            merged.currency, merged.dataTimestamp, 50.0, now) as CheapStockPolicy.Verdict.Accepted
        assertEquals(12.0, verdict.match.price, 0.0)
        assertFalse(merged.isRealtime)
    }

    @Test fun unknownAndForeignCurrencyAreNeverInventedAsTry() {
        for (currency in listOf(null, "USD", "EUR")) {
            val merged = DelayedQuoteMergePolicy.merge(stock("TRY", 10.0), stock(currency, 12.0))
            val verdict = CheapStockPolicy.evaluate(merged.quotePrice, null, merged.currency,
                merged.dataTimestamp, 50.0, now) as CheapStockPolicy.Verdict.Rejected
            assertEquals(CheapStockPolicy.Reject.CURRENCY_UNKNOWN, verdict.reason)
        }
    }

    @Test fun quoteTimestampTravelsWithQuotePriceAndSourceStaysDelayed() {
        val quote = stock("TRY", 12.0).copy(dataTimestamp = now + 10_000L, receivedAt = now + 11_000L,
            receivedElapsedRealtime = 20L, isRealtime = true, bid = 11.9, ask = 12.1)
        val merged = DelayedQuoteMergePolicy.merge(stock(null, 10.0), quote)
        assertEquals(quote.dataTimestamp, merged.dataTimestamp)
        assertEquals(quote.receivedAt, merged.receivedAt)
        assertEquals(20L, merged.receivedElapsedRealtime)
        assertFalse(merged.isRealtime)
        assertNull(merged.bid)
        assertNull(merged.ask)
    }

    @Test(expected = IllegalArgumentException::class)
    fun differentSymbolsCannotBeMerged() {
        DelayedQuoteMergePolicy.merge(stock("TRY", 10.0), stock("TRY", 12.0).copy(symbol = "OTHER"))
    }
}
