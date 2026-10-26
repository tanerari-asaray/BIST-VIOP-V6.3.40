package tr.borsatakip.v5.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tr.borsatakip.v5.analysis.ChartTimeframe
import tr.borsatakip.v5.analysis.OhlcvResampler
import tr.borsatakip.v5.model.Candle
import java.util.concurrent.ConcurrentHashMap

/** Chart-only provenance: delayed fallback must never be mistaken for a live trade feed. */
data class ChartHistoryResult(
    val candles: List<Candle>,
    val source: String,
    val delayed: Boolean,
    val lastCandleTime: Long?
)

/**
 * Keeps an optional display-only source separate from scan/provider routing. There is no
 * synthetic OHLCV: both sources must return genuine valid candles. An absent primary history
 * can use delayed observations without enabling Yahoo for signals or changing SettingsStore.
 */
class ChartHistoryRepository(
    private val provider: MarketDataProvider,
    private val delayedDisplayProvider: MarketDataProvider? = null
) {
    private data class CacheEntry(val result: ChartHistoryResult, val loadedAt: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val fallbackLimiter = Semaphore(2)

    suspend fun load(symbol: String, timeframe: ChartTimeframe, fallbackDaily: List<Candle>): List<Candle> =
        loadWithSource(symbol, timeframe, fallbackDaily).candles

    suspend fun loadWithSource(symbol: String, timeframe: ChartTimeframe, fallbackDaily: List<Candle>): ChartHistoryResult {
        val normalized = symbol.trim().uppercase()
        // The provided saved daily bars cannot fill a failed selected-timeframe request.
        val newest = OhlcvResampler.sanitize(fallbackDaily).lastOrNull()?.timestamp ?: 0L
        val key = "${provider.mtfCacheSourceKey()}|display=${delayedDisplayProvider?.id ?: "off"}:$normalized:${timeframe.name}:$newest"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.loadedAt in 0L..CACHE_TTL_MS }?.let { return it.result }

        var primaryError: Exception? = null
        val primary = try {
            fetch(provider, normalized, timeframe)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            primaryError = error
            emptyList()
        }
        var origin = provider.displayName
        var delayed = origin.contains("Yahoo", ignoreCase = true) || provider.id == "yahoo_fallback"
        val selected = if (primary.isNotEmpty()) primary else if (
            delayedDisplayProvider != null && BIST_SYMBOL.matches(normalized)
        ) {
            val fallback = try {
                fallbackLimiter.withPermit { fetch(delayedDisplayProvider, normalized, timeframe) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                emptyList()
            }
            if (fallback.isNotEmpty()) {
                origin = delayedDisplayProvider.displayName
                delayed = true
                fallback
            } else emptyList()
        } else emptyList()
        // If primary throws and there is no delayed source, preserve the original failure
        // for callers that need structured diagnostics.
        if (selected.isEmpty() && primaryError != null && delayedDisplayProvider == null) throw primaryError
        val bars = when {
            timeframe == ChartTimeframe.ONE_WEEK -> OhlcvResampler.weekly(selected)
            timeframe == ChartTimeframe.ONE_MONTH -> OhlcvResampler.monthly(selected)
            timeframe == ChartTimeframe.FOUR_HOUR -> OhlcvResampler.sessionAggregate(selected, 60, 240)
            timeframe.aggregateMinutes != null -> OhlcvResampler.aggregate(selected,
                requireNotNull(timeframe.requestIntervalMinutes), timeframe.aggregateMinutes)
            else -> selected
        }
        val result = ChartHistoryResult(bars, origin, delayed, bars.lastOrNull()?.timestamp)
        // Don't cache failed lookups; a retry after restoring connectivity should work promptly.
        if (result.candles.isNotEmpty()) cache[key] = CacheEntry(result, now)
        return result
    }

    private suspend fun fetch(source: MarketDataProvider, symbol: String, tf: ChartTimeframe): List<Candle> = withContext(Dispatchers.IO) {
        val raw = if (tf.daily) source.fetchDailyHistory(symbol, tf.maximumRange) else {
            val to = System.currentTimeMillis()
            source.fetchHistory(symbol, to - requireNotNull(tf.lookbackMs), to,
                requireNotNull(tf.requestIntervalMinutes))
        }
        OhlcvResampler.sanitize(raw).filter { it.timestamp <= System.currentTimeMillis() + 60_000L }
    }

    fun invalidate(symbol: String? = null) {
        if (symbol == null) cache.clear() else {
            val keyPart = ":" + symbol.trim().uppercase() + ":"
            cache.keys.filter { keyPart in it }.forEach(cache::remove)
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 60_000L
        private val BIST_SYMBOL = Regex("[A-Z0-9_]{3,12}")
    }
}
