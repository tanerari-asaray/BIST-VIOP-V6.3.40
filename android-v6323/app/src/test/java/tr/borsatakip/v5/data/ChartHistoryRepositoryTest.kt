package tr.borsatakip.v5.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.analysis.ChartTimeframe
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class ChartHistoryRepositoryTest {
    private class Provider : MarketDataProvider {
        override val id = "test"; override val displayName = "fixture"
        var source = "A"; var calls = 0; var rows: List<Candle> = emptyList()
        override fun mtfCacheSourceKey() = source
        override suspend fun scan(onProgress: (Int, Int) -> Unit): List<Stock> = emptyList()
        override suspend fun fetchOne(symbol: String): Stock? = null
        override suspend fun fetchDailyHistory(symbol: String, maximumRange: Boolean): List<Candle> { calls++; return rows }
    }
    private val bars = listOf(Candle(1L, 10.0, 12.0, 9.0, 11.0, 100.0))
    @Test fun emptyProviderResultNeverSilentlyFallsBackToSavedDailyPrice() = runBlocking {
        val provider = Provider()
        assertTrue(ChartHistoryRepository(provider).load("TEST", ChartTimeframe.ONE_DAY, bars).isEmpty())
    }
    @Test fun cacheSeparatesSourceOriginsAndSymbolInvalidationWorks() = runBlocking {
        val provider = Provider().apply { rows = bars }
        val repository = ChartHistoryRepository(provider)
        repository.load("TEST", ChartTimeframe.ONE_DAY, emptyList())
        repository.load("TEST", ChartTimeframe.ONE_DAY, emptyList())
        assertEquals(1, provider.calls)
        provider.source = "B"; provider.rows = emptyList()
        assertTrue(repository.load("TEST", ChartTimeframe.ONE_DAY, emptyList()).isEmpty())
        assertEquals(2, provider.calls)
        repository.invalidate("TEST")
        repository.load("TEST", ChartTimeframe.ONE_DAY, emptyList())
        assertEquals(3, provider.calls)
    }
}
