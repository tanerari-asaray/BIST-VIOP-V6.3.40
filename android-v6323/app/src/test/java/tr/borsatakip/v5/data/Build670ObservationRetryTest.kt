package tr.borsatakip.v5.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle

class Build670ObservationRetryTest {
    @Test fun transientFailuresRecoverWithRecordedAttempts() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        val result = ObservationRetryPolicy.execute(
            fetch = { calls++; if (calls < 3) error("timeout") else "valid" },
            validate = { null }, classify = { "NETWORK_TIMEOUT" }, pause = { waits += it })
        assertEquals("valid", result.value); assertNull(result.error)
        assertEquals(3, result.attempts)
        assertEquals(listOf("NETWORK_TIMEOUT", "NETWORK_TIMEOUT"), result.errors)
        assertEquals(listOf(500L, 1000L), waits)
    }

    @Test fun permanentFailureDoesNotLoop() = runBlocking {
        val result = ObservationRetryPolicy.execute<String>(
            fetch = { error("authentication") }, validate = { null },
            classify = { "AUTH_ERROR" }, pause = { fail("Must not retry authentication") })
        assertEquals(1, result.attempts); assertEquals("AUTH_ERROR", result.error)
    }

    @Test fun emptyDataStopsAfterThreeAttempts() = runBlocking {
        val result = ObservationRetryPolicy.execute<String>(fetch = { null }, validate = { null },
            classify = { "PROVIDER_ERROR" }, pause = {})
        assertEquals(3, result.attempts); assertEquals("EMPTY_DATA", result.error)
    }

    @Test fun cancellationIsPropagated() = runBlocking {
        try {
            ObservationRetryPolicy.execute<String>(fetch = { throw CancellationException("cancel") },
                validate = { null }, classify = { "PROVIDER_ERROR" }, pause = {})
            fail("Cancellation must escape")
        } catch (_: CancellationException) { /* expected */ }
    }

    private fun row(symbol: String, good: Boolean) = FullUniversePolicy.Entry(
        FullUniversePolicy.Market.BIST, symbol, "TEST", if (good) 10.0 else null, "TRY",
        FullUniversePolicy.Trend.UNKNOWN, FullUniversePolicy.Quality.DELAYED, "TEST", 1L, 1L, "TEST",
        reasonCode = if (good) null else "NETWORK_TIMEOUT",
        candles = if (good) listOf(Candle(1L, 10.0, 11.0, 9.0, 10.0, 1.0)) else emptyList())

    @Test fun retryCoversAll632IncludingFailedAndNeverAttemptedSymbols() = runBlocking {
        val universe = (1..632).map { "T%04d".format(it) }
        val saved = universe.take(150).map { row(it, true) } + universe.drop(150).take(50).map { row(it, false) }
        val remaining = ObservationRetryPolicy.remaining(universe, saved, true)
        assertEquals(482, remaining.size)
        val visited = saved.filter { it.price != null }.map { it.symbol }.toMutableList()
        for (symbol in remaining) {
            val result = ObservationRetryPolicy.execute(fetch = { symbol }, validate = { null },
                classify = { "PROVIDER_ERROR" }, pause = {})
            assertNull(result.error); visited += requireNotNull(result.value)
        }
        assertEquals(632, visited.size); assertEquals(universe.toSet(), visited.toSet())
    }

    @Test fun unsuccessfulQuoteValidationCannotLeakPriceIntoResults() = runBlocking {
        val result = ObservationRetryPolicy.execute(
            fetch = { 0.50 },
            validate = { price: Double -> if (price > 1.0) null else "PRICE_UNVERIFIED" },
            classify = { "NETWORK_ERROR" },
            pause = {}
        )
        assertNull(result.value)
        assertEquals("PRICE_UNVERIFIED", result.error)
        assertEquals(1, result.attempts)
    }

    @Test fun incompleteHistoryIsRetriedWithoutAcceptingUnvalidatedObservation() = runBlocking {
        var attempt = 0
        val result = ObservationRetryPolicy.execute(fetch = { if (++attempt == 1) "quote" else null },
            validate = { "HISTORY_EMPTY" }, classify = { "PROVIDER_ERROR" }, pause = {})
        assertNull(result.value); assertEquals("EMPTY_DATA", result.error); assertEquals(3, result.attempts)
        assertEquals(listOf("HISTORY_EMPTY", "EMPTY_DATA", "EMPTY_DATA"), result.errors)
    }
}
