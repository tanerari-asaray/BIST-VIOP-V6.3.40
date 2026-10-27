package tr.borsatakip.v5.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercises the same retry engine used by both VIOP quote and history stages. */
class Build670ViopStageRetryTest {
    @Test fun quoteFailureDoesNotCreateSuccessfulQuote() = runBlocking {
        var attempts = 0
        val result = ObservationRetryPolicy.execute(
            fetch = {
                attempts++
                throw IllegalStateException("provider unavailable")
                "unreachable"
            },
            validate = { _: String -> null },
            classify = { "AUTH_FAILURE" },
            pause = {}
        )
        assertNull(result.value)
        assertEquals("AUTH_FAILURE",result.error)
        assertEquals(1,result.attempts)
        assertEquals(1,attempts)
    }

    @Test fun independentHistoryTimeoutDoesNotEraseSuccessfulQuote() = runBlocking {
        val quote = ObservationRetryPolicy.execute(
            fetch = { 49.25 },
            validate = { price: Double -> if(price>0) null else "EMPTY_DATA" },
            classify = { "NETWORK_ERROR" },
            pause = {}
        )
        var tries=0
        val history = ObservationRetryPolicy.execute(
            fetch = { tries++; emptyList<Double>() },
            validate = { candles: List<Double> -> if(candles.isEmpty()) "HISTORY_EMPTY" else null },
            classify = { "NETWORK_ERROR" },
            pause = {}
        )
        assertEquals(49.25,quote.value!!,0.0)
        assertNull(quote.error)
        assertEquals("HISTORY_EMPTY",history.error)
        assertEquals(3,tries)
    }

    @Test fun network429RetriesAreBounded() = runBlocking {
        var calls=0
        val pauses=mutableListOf<Long>()
        val result = ObservationRetryPolicy.execute(
            fetch = { calls++; if(calls<3) throw IllegalStateException("429"); 3 },
            validate = { _: Int -> null },
            classify = { "RATE_LIMIT" },
            pause = { pauses+=it }
        )
        assertEquals(3,result.value)
        assertEquals(3,calls)
        assertEquals(listOf(2000L,4000L),pauses)
    }
}
