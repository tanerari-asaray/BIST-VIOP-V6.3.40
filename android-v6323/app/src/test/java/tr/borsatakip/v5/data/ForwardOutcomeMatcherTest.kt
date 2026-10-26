package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class ForwardOutcomeMatcherTest {
    private fun candle(ts: Long, close: Double) = Candle(ts, close, close, close, close, 1.0)

    @Test fun exactTargetMatches() {
        val target = 1_000_000L
        val c = ForwardOutcomeMatcher.match(listOf(candle(target, 101.0)), target, 30 * 60_000L)
        assertEquals(target, c?.timestamp)
    }

    @Test fun twentyMinuteDelayStillMatchesIntraday() {
        val target = 1_000_000L
        val delayed = target + 20 * 60_000L
        val c = ForwardOutcomeMatcher.match(listOf(candle(delayed, 101.0)), target, ForwardOutcomeMatcher.toleranceMs("15m"))
        assertEquals(delayed, c?.timestamp)
    }

    @Test fun eightHourLateQuoteDoesNotMasqueradeAs15m() {
        val target = 1_000_000L
        val late = target + 8 * 60 * 60_000L
        assertNull(ForwardOutcomeMatcher.match(listOf(candle(late, 101.0)), target, ForwardOutcomeMatcher.toleranceMs("15m")))
    }

    @Test fun oneDayHorizonCanBridgeWeekendWithin72h() {
        val target = 1_000_000L
        val mondayLike = target + 48 * 60 * 60_000L
        assertNotNull(ForwardOutcomeMatcher.match(listOf(candle(mondayLike, 101.0)), target, ForwardOutcomeMatcher.toleranceMs("1d")))
    }

    @Test fun missingHistoryReturnsNull() {
        assertNull(ForwardOutcomeMatcher.match(emptyList(), 1_000_000L, ForwardOutcomeMatcher.toleranceMs("30m")))
    }

    @Test fun allHorizonsCanBecomeDueInSingleUpdate() {
        val start = 1_000_000L
        val now = start + 2 * 24 * 60 * 60_000L
        val due = ForwardOutcomeMatcher.dueHorizons(start, now)
        assertEquals(setOf("15m", "30m", "1h", "4h", "1d"), due.keys)
    }

    @Test fun shortDirectionInvertsReturn() {
        val v = ForwardOutcomeMatcher.directionalReturnPct(100.0, 90.0, "SHORT")
        assertEquals(10.0, v ?: Double.NaN, 1e-9)
    }
}
