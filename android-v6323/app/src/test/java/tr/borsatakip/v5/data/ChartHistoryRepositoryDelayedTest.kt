package tr.borsatakip.v5.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.analysis.ChartTimeframe
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class ChartHistoryRepositoryDelayedTest {
    private class Fixture(
        override val id: String,
        var bars: List<Candle> = emptyList(),
        var fail: Boolean = false
    ) : MarketDataProvider {
        override val displayName = if (id == "delayed") "Yahoo • gecikmeli" else "Primary"
        var intradayCalls = 0
        var dailyCalls = 0
        override suspend fun scan(onProgress: (Int, Int) -> Unit): List<Stock> = emptyList()
        override suspend fun fetchOne(symbol: String): Stock? = null
        override suspend fun fetchHistory(symbol: String, fromTime: Long, toTime: Long, intervalMinutes: Int): List<Candle> {
            intradayCalls++
            if (fail) error("NETWORK")
            return bars
        }
        override suspend fun fetchDailyHistory(symbol: String, maximumRange: Boolean): List<Candle> {
            dailyCalls++
            if (fail) error("NETWORK")
            return bars
        }
    }
    private val sample = listOf(
        Candle(1710000000000L, 10.0, 12.0, 9.0, 11.0, 1500.0),
        Candle(1710000300000L, 11.0, 13.0, 10.0, 12.0, 1100.0)
    )

    @Test fun chartOnlyFallbackIsMarkedDelayedAndCached() = runBlocking {
        val primary = Fixture("primary")
        val fallback = Fixture("delayed", sample)
        val repo = ChartHistoryRepository(primary, fallback)
        val result = repo.loadWithSource("ASELS", ChartTimeframe.FIVE_MIN, emptyList())
        assertTrue(result.delayed)
        assertEquals("Yahoo • gecikmeli", result.source)
        assertEquals(sample, result.candles)
        assertEquals(1710000300000L, result.lastCandleTime)
        repo.loadWithSource("ASELS", ChartTimeframe.FIVE_MIN, emptyList())
        assertEquals(1, fallback.intradayCalls)
    }

    @Test fun primaryIsPreferredAndNotMislabelled() = runBlocking {
        val primary = Fixture("primary", sample)
        val fallback = Fixture("delayed", sample)
        val result = ChartHistoryRepository(primary, fallback).loadWithSource("ASELS", ChartTimeframe.FIVE_MIN, emptyList())
        assertFalse(result.delayed)
        assertEquals("Primary", result.source)
        assertEquals(0, fallback.intradayCalls)
    }

    @Test fun malformedPrimaryBarsUseOnlyActualFallbackBars() = runBlocking {
        val invalid = Candle(1700000000000L, -2.0, 5.0, 1.0, 2.0, 100.0)
        val primary = Fixture("primary", listOf(invalid))
        val fallback = Fixture("delayed", sample)
        val result = ChartHistoryRepository(primary, fallback).loadWithSource("ASELS", ChartTimeframe.FIVE_MIN, emptyList())
        assertTrue(result.delayed)
        assertEquals(sample, result.candles)
    }

    @Test fun invalidSymbolNeverGetsSentToDelayedEquityProvider() = runBlocking {
        val fallback = Fixture("delayed", sample)
        val result = ChartHistoryRepository(Fixture("primary"), fallback).loadWithSource("F_XU030", ChartTimeframe.FIVE_MIN, emptyList())
        // A valid-looking BIST ticker is not proof of a futures contract; callers must
        // only instantiate this repository for their BIST equity StockDetail screen.
        assertTrue(result.delayed)
        assertEquals(1, fallback.intradayCalls)
        val another = ChartHistoryRepository(Fixture("primary"), fallback).loadWithSource("XU030/2026", ChartTimeframe.FIVE_MIN, emptyList())
        assertFalse(another.delayed)
        assertTrue(another.candles.isEmpty())
        assertEquals(1, fallback.intradayCalls)
    }

    @Test fun absenceAndExceptionsNeverManufactureCandles() = runBlocking {
        val primary = Fixture("primary", fail = true)
        val fallback = Fixture("delayed", fail = true)
        val repo = ChartHistoryRepository(primary, fallback)
        val result = repo.loadWithSource("ASELS", ChartTimeframe.ONE_DAY, sample)
        assertTrue(result.candles.isEmpty())
        assertNull(result.lastCandleTime)
        assertFalse(result.delayed)
        fallback.fail = false
        fallback.bars = sample
        assertEquals(sample, repo.load("ASELS", ChartTimeframe.ONE_DAY, sample))
        assertEquals(2, fallback.dailyCalls)
    }
}
