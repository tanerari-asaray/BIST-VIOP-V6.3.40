package tr.borsatakip.v5.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Bounded retries; cancellation and configuration/authentication failures are never hidden. */
object ObservationRetryPolicy {
    data class Result<T>(val value: T?, val error: String?, val attempts: Int, val errors: List<String>)
    private val transient = setOf("RATE_LIMIT", "NETWORK_TIMEOUT", "NETWORK_ERROR", "DNS_ERROR",
        "SERVICE_UNAVAILABLE", "SERVER_ERROR", "EMPTY_DATA", "HISTORY_EMPTY")

    suspend fun <T> execute(
        fetch: suspend () -> T?,
        validate: (T) -> String?,
        classify: (Exception) -> String,
        pause: suspend (Long) -> Unit = { delay(it) }
    ): Result<T> {
        val errors = mutableListOf<String>()
        var lastValue: T? = null
        for (attempt in 1..3) {
            var value: T? = null
            val error = try {
                value = withTimeout(22_000L) { fetch() }
                if (value == null) "EMPTY_DATA" else validate(value)
            } catch (_: TimeoutCancellationException) { "NETWORK_TIMEOUT" }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { classify(error) }
            if (value != null) lastValue = value
            if (error == null) return Result(value, null, attempt, errors)
            errors += error
            if (error !in transient || attempt == 3) return Result(lastValue, error, attempt, errors)
            pause((if (error == "RATE_LIMIT") 2_000L else 500L) * attempt)
        }
        error("Unreachable retry state")
    }

    /** Retry failures AND never-attempted records, including an interrupted checkpoint. */
    fun remaining(universe: List<String>, rows: List<FullUniversePolicy.Entry>, retryFailures: Boolean): List<String> {
        val done = rows.filter { it.market == FullUniversePolicy.Market.BIST &&
            (!retryFailures || (it.price != null && it.reasonCode == null && it.candles.isNotEmpty())) }
            .map { it.symbol }.toSet()
        return universe.distinct().filterNot { it in done }
    }
}
