package tr.borsatakip.v5.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class ScanTimeframeEngineTest {
    @Test
    fun quickTimeframesContainRealDailyAndNever240() {
        assertEquals(listOf("1 DK", "3 DK", "5 DK", "10 DK", "15 DK", "30 DK", "60 DK", "1 GÜN"), ScanTimeframe.quick().map { it.label })
        assertEquals(1440, ScanTimeframe.DAILY.storedMinutes)
        assertEquals("1d", ScanTimeframe.DAILY.apiInterval)
        assertFalse(ScanTimeframe.isSupportedStored(240))
        assertEquals(5, ScanTimeframe.normalizeStoredMinutes(240))
    }

    @Test
    fun fiveMinuteScanRequestsFiveMinuteOhlcvDirectly() = runBlocking {
        val fake = FakeProvider()
        val provider = IntervalMarketDataProvider(fake, 5, minimumBars = 3)
        val rows = provider.scan { _, _ -> }
        assertEquals(listOf(5), fake.historyIntervals)
        assertEquals("5m", rows.single().interval)
        assertEquals(5, rows.single().candles.size)
    }

    @Test
    fun threeMinuteScanUsesRealOneMinuteOhlcvThenResamples() = runBlocking {
        val fake = FakeProvider()
        val provider = IntervalMarketDataProvider(fake, 3, minimumBars = 3)
        val rows = provider.scan { _, _ -> }
        assertEquals(listOf(1), fake.historyIntervals)
        assertEquals("3m", rows.single().interval)
        assertTrue(rows.single().candles.size >= 3)
    }

    @Test
    fun dailyScanUsesRealDailyHistoryAndNeverRequests240Minutes() = runBlocking {
        val fake = FakeProvider(baseInterval = "5m")
        val provider = IntervalMarketDataProvider(fake, ScanTimeframe.DAILY_STORED_MINUTES, minimumBars = 3)
        val rows = provider.scan { _, _ -> }
        assertEquals(1, fake.dailyCalls)
        assertFalse(fake.historyIntervals.contains(240))
        assertEquals("1d", rows.single().interval)
    }

    @Test
    fun changingTimeframeCreatesFreshDataRequestInsteadOfReusingPreviousRows() = runBlocking {
        val fake = FakeProvider()
        IntervalMarketDataProvider(fake, 5, minimumBars = 3).scan { _, _ -> }
        IntervalMarketDataProvider(fake, 15, minimumBars = 3).scan { _, _ -> }
        assertEquals(listOf(5, 15), fake.historyIntervals)
    }

    private class FakeProvider(private val baseInterval: String = "1d") : MarketDataProvider {
        override val id = "fake"
        override val displayName = "fake"
        val historyIntervals = mutableListOf<Int>()
        var dailyCalls = 0
        private val now = (System.currentTimeMillis() / 60_000L) * 60_000L

        override suspend fun listSymbols(): List<String> = listOf("KIMMR")
        override suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<Stock> {
            onProgress(0, 1)
            onProgress(1, 1)
            return listOf(baseStock())
        }
        override suspend fun fetchOne(symbol: String): Stock = baseStock()
        override suspend fun fetchHistory(symbol: String, fromTime: Long, toTime: Long, intervalMinutes: Int): List<Candle> {
            historyIntervals += intervalMinutes
            val count = if (intervalMinutes == 1) 15 else 5
            return candles(count, intervalMinutes)
        }
        override suspend fun fetchDailyHistory(symbol: String, maximumRange: Boolean): List<Candle> {
            dailyCalls++
            return List(5) { i -> candle(now - (4L - i) * 86_400_000L, 20.0 + i) }
        }

        private fun baseStock() = Stock(
            symbol = "KIMMR", companyName = "Test", candles = candles(5, 1), source = "fake",
            dataTimestamp = now, isRealtime = true, delaySeconds = 0, currentSessionIncluded = true,
            receivedAt = now, receivedElapsedRealtime = 1L, quotePrice = 25.0, market = "BIST",
            historySymbol = "KIMMR", interval = baseInterval, exchangeTimezone = "Europe/Istanbul",
            lastBarTime = now, lastBarClosed = true, previousClose = 24.0
        )

        private fun candles(count: Int, minutes: Int): List<Candle> = List(count) { i ->
            val ts = now - (count - 1L - i) * minutes * 60_000L
            candle(ts, 20.0 + i * 0.1)
        }
        private fun candle(ts: Long, close: Double) = Candle(ts, close, close + 0.2, close - 0.2, close, 1000.0)
    }
}
