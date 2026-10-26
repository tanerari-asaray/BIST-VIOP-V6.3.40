package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.ViopQuote

class ViopQuoteDisplayPolicyTest {
    private val now = 1_792_500_000_000L
    private fun quote(change: Double? = 1.5) = ViopQuote(
        symbol = "F_TEST1026", price = 100.0, bid = 99.9, ask = 100.1,
        dailyChangePct = change, volume = 1_000.0, openInterest = 500,
        exchangeTimestamp = now - 1_000L, receivedAt = now, source = "controlled-test",
        realtime = true, delaySeconds = 0, currentSessionIncluded = true
    )

    @Test fun positiveChangeKeepsValueAndUpDirection() {
        val display = ViopQuoteDisplayPolicy.from(quote(), now)
        assertEquals(1.5, display.changePct!!, 0.0)
        assertEquals(1, display.changeDirection)
    }

    @Test fun negativeChangeKeepsValueAndDownDirection() {
        val display = ViopQuoteDisplayPolicy.from(quote(-2.5), now)
        assertEquals(-2.5, display.changePct!!, 0.0)
        assertEquals(-1, display.changeDirection)
    }

    @Test fun zeroChangeIsNeutral() {
        listOf(0.0, -0.0).forEach { value ->
            assertEquals(0, ViopQuoteDisplayPolicy.from(quote(value), now).changeDirection)
        }
    }

    @Test fun missingAndNonFiniteChangesHaveNoDirectionalColor() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            val display = ViopQuoteDisplayPolicy.from(quote(value), now)
            assertNull(display.changePct)
            assertEquals(0, display.changeDirection)
        }
    }

    @Test fun exactFreshnessBoundaryIsLive() {
        assertTrue(ViopQuoteDisplayPolicy.from(quote().copy(exchangeTimestamp = now), now).isLive)
        assertTrue(ViopQuoteDisplayPolicy.from(quote().copy(exchangeTimestamp = now - 60_000L), now).isLive)
        assertFalse(ViopQuoteDisplayPolicy.from(quote().copy(exchangeTimestamp = now - 60_001L), now).isLive)
    }

    @Test fun unknownFutureAndExtremeTimestampsAreNotLive() {
        listOf(0L, -1L, now + 1L, Long.MIN_VALUE, Long.MAX_VALUE).forEach { time ->
            assertFalse(ViopQuoteDisplayPolicy.from(quote().copy(exchangeTimestamp = time), now).isLive)
        }
    }

    @Test fun sourceDelayAndSessionMustAllBeVerified() {
        listOf(quote().copy(realtime = false), quote().copy(delaySeconds = null),
            quote().copy(delaySeconds = 1), quote().copy(currentSessionIncluded = false)).forEach { value ->
            assertFalse(ViopQuoteDisplayPolicy.from(value, now).isLive)
        }
    }

    @Test fun invalidPriceCannotCarryLiveLabel() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            assertFalse(ViopQuoteDisplayPolicy.from(quote().copy(price = value), now).isLive)
        }
    }

    @Test fun rowUsesAssessmentTimeInsteadOfWallClock() {
        val value = quote().copy(exchangeTimestamp = now - 60_000L)
        assertTrue(ViopQuoteDisplayPolicy.from(value, now).isLive)
        assertFalse(ViopQuoteDisplayPolicy.from(value, now + 1L).isLive)
        assertTrue(ViopQuoteDisplayPolicy.from(value, now).isLive)
    }
}
