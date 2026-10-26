package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StocksReferencePolicyTest {
    private val now = Instant.parse("2026-10-19T19:00:00Z").toEpochMilli()
    private val names = (0..29).associate { "TEST$it" to "TEST VERİSİ $it" }
    private fun directory() = KapStockDirectory(names, names.keys, now)

    @Test fun completeThirtyIsRequiredAndEveryMemberMustHaveAName() {
        assertTrue(directory().usable(now))
        assertFalse(directory().copy(bist30 = names.keys.take(29).toSet()).valid())
        assertFalse(directory().copy(bist30 = names.keys + "UNKNOWN").valid())
        assertFalse(directory().copy(names = names - "TEST0").valid())
        assertFalse(directory().copy(names = names + ("TEST0" to "")).valid())
    }
    @Test fun oldFutureAndSecondsBasedDirectoriesAreNotCurrentMembership() {
        assertFalse(directory().usable(now - 1))
        assertFalse(directory().usable(now + 3_600_001))
        assertFalse(directory().copy(fetchedAt = now / 1000).usable(now))
    }
    @Test fun MembershipExpiresAtIstanbulCalendarRolloverEvenWithinAnHour() {
        val before = Instant.parse("2026-10-19T20:59:00Z").toEpochMilli()
        assertFalse(directory().copy(fetchedAt = before).usable(before + 120000))
    }
    @Test fun delayedQuoteIsNeverLiveAndMissingChangeRemainsMissing() {
        val q = StockIndexQuote("XU100", 100.0, null, now, "TEST VERİSİ", false)
        assertTrue(q.valid("XU100", now)); assertFalse(q.live(now)); assertNull(q.changePct)
    }
    @Test fun quoteIdentityPriceTimeAndSourceMustBeValid() {
        val q = StockIndexQuote("XU100", 100.0, 0.0, now, "TEST VERİSİ")
        assertFalse(q.valid("XU030", now))
        assertFalse(q.copy(price = Double.NaN).valid("XU100", now))
        assertFalse(q.copy(exchangeTimestamp = now / 1000).valid("XU100", now))
        assertFalse(q.copy(exchangeTimestamp = now + 60001).valid("XU100", now))
        assertFalse(q.copy(changePct = Double.POSITIVE_INFINITY).valid("XU100", now))
        assertFalse(q.copy(source = "").valid("XU100", now))
    }
    @Test fun liveDeclarationExpiresWithoutChangingTheExchangeTimestamp() {
        val q = StockIndexQuote("XU030", 10.0, 0.0, now, "TEST VERİSİ", true)
        assertTrue(q.live(now)); assertFalse(q.live(now + 60001)); assertFalse(q.live(now - 1))
        assertEquals(now, q.exchangeTimestamp)
    }
}
