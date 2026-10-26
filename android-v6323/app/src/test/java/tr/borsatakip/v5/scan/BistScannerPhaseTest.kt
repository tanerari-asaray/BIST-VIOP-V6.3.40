package tr.borsatakip.v5.scan

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.data.MarketDataProvider
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class BistScannerPhaseTest {
    private val noHistory = object : HistoryRecorder {
        override suspend fun record(state: ScanState) = HistoryRecordResult(inserted = state.results.size)
    }

    @Test
    fun realScanStateMachineMovesThroughOperationalPhasesInOrder() = runBlocking {
        val now = System.currentTimeMillis()
        val stocks = listOf(stock("AAA", now), stock("BBB", now))
        val provider = object : MarketDataProvider {
            override val id = "phase_1d"
            override val displayName = "phase"
            override suspend fun listSymbols() = listOf("AAA", "BBB")
            override suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<Stock> {
                onProgress(0, 2)
                onProgress(1, 2)
                onProgress(2, 2)
                return stocks
            }
            override suspend fun fetchOne(symbol: String) = stocks.firstOrNull { it.symbol == symbol }
        }
        val phases = mutableListOf<ScanPhase>()
        BistScanner(provider, noHistory, BistScanMode.DELAYED_ANALYSIS).scan { phases += it.phase }
        val required = listOf(
            ScanPhase.READY, ScanPhase.STARTING, ScanPhase.LOADING_SYMBOLS, ScanPhase.LOADING_DATA,
            ScanPhase.SCANNING, ScanPhase.CALCULATING, ScanPhase.RANKING, ScanPhase.COMPLETED
        )
        var cursor = -1
        required.forEach { phase ->
            val next = phases.indexOfFirstIndexed(cursor + 1) { it == phase }
            assertTrue("$phase sırası bulunamadı: $phases", next > cursor)
            cursor = next
        }
    }

    @Test
    fun noOhlcvNeverReportsCompleted() = runBlocking {
        val provider = object : MarketDataProvider {
            override val id = "empty_5m"
            override val displayName = "empty"
            override suspend fun listSymbols() = listOf("KIMMR", "THYAO")
            override suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<Stock> {
                onProgress(0, 2); onProgress(1, 2); onProgress(2, 2); return emptyList()
            }
            override suspend fun fetchOne(symbol: String): Stock? = null
        }
        val state = BistScanner(provider, noHistory).scan { }
        assertEquals(ScanStatus.ERROR, state.status)
        assertEquals(ScanPhase.NO_DATA, state.phase)
        assertTrue(state.errorMessage?.contains("VERİ KULLANILAMIYOR") == true)
    }

    private fun stock(symbol: String, now: Long): Stock = Stock(
        symbol = symbol, companyName = symbol,
        candles = List(240) { i ->
            val close = 20.0 + i * 0.03
            Candle(now - (239L - i) * 86_400_000L, close, close + 0.4, close - 0.4, close, 1000.0 + i)
        },
        source = "unit", dataTimestamp = now, isRealtime = false, currentSessionIncluded = false,
        receivedAt = now, receivedElapsedRealtime = 1L, market = "BIST", historySymbol = symbol,
        interval = "1d", exchangeTimezone = "Europe/Istanbul", lastBarTime = now, lastBarClosed = true,
        previousClose = 26.0
    )

    private fun <T> List<T>.indexOfFirstIndexed(start: Int, predicate: (T) -> Boolean): Int {
        for (i in start.coerceAtLeast(0) until size) if (predicate(this[i])) return i
        return -1
    }
}
