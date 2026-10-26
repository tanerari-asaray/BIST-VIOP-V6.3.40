package tr.borsatakip.v5.scan

import org.junit.Assert.*
import org.junit.Test

class CheapStockPolicyTest {
    private val now = 1_800_000_000_000L
    @Test fun priceAtLimitIsAllowed() {
        val x = CheapStockPolicy.evaluate(50.0, null, "TRY", now, 50.0, now)
        assertEquals(50.0, (x as CheapStockPolicy.Verdict.Accepted).match.price, 0.0)
    }
    @Test fun priceAboveLimitIsNotCalledCheap() {
        assertEquals(CheapStockPolicy.Reject.ABOVE_LIMIT,
            (CheapStockPolicy.evaluate(50.01, null, "TRY", now, 50.0, now) as CheapStockPolicy.Verdict.Rejected).reason)
    }
    @Test fun nonTryAndUnknownCurrencyDoNotPass() {
        for (currency in listOf("USD", "EUR", null)) {
            assertEquals(CheapStockPolicy.Reject.CURRENCY_UNKNOWN,
                (CheapStockPolicy.evaluate(12.0, null, currency, now, 50.0, now) as CheapStockPolicy.Verdict.Rejected).reason)
        }
    }
    @Test fun staleAndFutureDataDoNotPass() {
        for (t in listOf(now - CheapStockPolicy.MAX_QUOTE_AGE_MS - 1L, now + 130_000L, 0L)) {
            assertEquals(CheapStockPolicy.Reject.STALE_OR_INVALID_TIME,
                (CheapStockPolicy.evaluate(12.0, null, "TRY", t, 50.0, now) as CheapStockPolicy.Verdict.Rejected).reason)
        }
    }
    @Test fun invalidPricesFailAndCandleFallbackIsMarked() {
        assertEquals(CheapStockPolicy.Reject.PRICE_INVALID,
            (CheapStockPolicy.evaluate(Double.NaN, -2.0, "TRY", now, 50.0, now) as CheapStockPolicy.Verdict.Rejected).reason)
        val x = CheapStockPolicy.evaluate(null, 19.75, "TL", now, 50.0, now)
        assertEquals(CheapStockPolicy.PriceKind.LAST_CANDLE_CLOSE, (x as CheapStockPolicy.Verdict.Accepted).match.kind)
    }
}