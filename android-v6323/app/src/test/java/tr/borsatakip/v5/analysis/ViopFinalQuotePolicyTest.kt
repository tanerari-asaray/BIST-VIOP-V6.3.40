package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test

class ViopFinalQuotePolicyTest {
    private val now = 1_793_000_000_000L

    private fun evaluate(
        symbol: String = "F_TEST1026", ageMs: Long = 200L, price: Double = 100.0,
        realtime: Boolean = true, delay: Int? = 0, session: Boolean = true,
        timeOverride: Long? = null
    ) = ViopFinalQuotePolicy.rejectReason(
        "F_TEST1026", symbol, price, timeOverride ?: now - ageMs, now,
        realtime, delay, session
    )

    @Test fun validLiveQuotePasses() = assertNull(evaluate())
    @Test fun staleQuoteCannotBePublished() = assertEquals("FINAL_STALE_DATA", evaluate(ageMs=60_001L))
    @Test fun secondsTimestampCannotBePublished() = assertEquals("FINAL_TIMESTAMP_MUST_BE_MILLISECONDS", evaluate(timeOverride=1_793_000_000L))
    @Test fun futureQuoteCannotBePublished() = assertEquals("FINAL_FUTURE_TIMESTAMP", evaluate(timeOverride=now + 15_001L))
    @Test fun mismatchedSymbolCannotBePublished() = assertEquals("FINAL_SYMBOL_MISMATCH", evaluate(symbol="F_OTHER1026"))
    @Test fun invalidPriceCannotBePublished() = assertEquals("FINAL_INVALID_PRICE", evaluate(price=0.0))
    @Test fun delayedQuoteCannotBePublished() = assertEquals("FINAL_DELAYED_OR_UNKNOWN", evaluate(delay=3))
    @Test fun missingDelayCannotBePublished() = assertEquals("FINAL_DELAYED_OR_UNKNOWN", evaluate(delay=null))
    @Test fun unverifiedSessionCannotBePublished() = assertEquals("FINAL_REALTIME_NOT_VERIFIED", evaluate(session=false))
    @Test fun notRealtimeCannotBePublished() = assertEquals("FINAL_REALTIME_NOT_VERIFIED", evaluate(realtime=false))
    @Test fun boundaryAgeAllowed() = assertNull(evaluate(ageMs=60_000L))
}
