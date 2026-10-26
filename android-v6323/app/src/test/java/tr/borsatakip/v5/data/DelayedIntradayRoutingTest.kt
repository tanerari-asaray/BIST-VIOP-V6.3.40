package tr.borsatakip.v5.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.Stock

class DelayedIntradayRoutingTest {
    private class RoutedFallback(private val failures: Boolean = false) : MarketDataProvider,
        DelayedIntradayScanSource, ScanDiagnosticsSource {
        override val id = "provider_router"
        override val displayName = "Delayed fallback through router"
        var bootstrapCalls = 0
        override fun useDirectDelayedIntradayScan() = true
        override fun scanDiagnostics() = ProviderScanDiagnostics(noDataSymbols = listOf("PREVIOUS_RUN"))
        override suspend fun listSymbols() = if (failures) listOf("GOOD", "MISSING", "LIMITED") else listOf("GOOD")
        override suspend fun scan(onProgress: (Int, Int) -> Unit): List<Stock> {
            bootstrapCalls++
            error("Intraday scanning must not require daily bootstrap")
        }
        override suspend fun fetchOne(symbol: String): Stock? = null
        override suspend fun fetchHistory(symbol: String, fromTime: Long, toTime: Long, intervalMinutes: Int): List<Candle> {
            if (symbol == "MISSING") throw ProviderException(ProviderFailureCode.EMPTY_DATA, "HTTP 404",
                diagnosticCode = "YAHOO_HTTP_404")
            if (symbol == "LIMITED") throw ProviderException(ProviderFailureCode.AUTH_ERROR, "HTTP 403",
                diagnosticCode = "YAHOO_HTTP_403")
            val end = (System.currentTimeMillis() / 300_000L) * 300_000L - 300_000L
            return List(5) { i -> Candle(end - (4L - i) * 300_000L, 10.0, 11.0, 9.0, 10.0, 100.0) }
        }
    }

    @Test fun routerFallbackScansSelectedPeriodWithoutDailyBootstrap() = runBlocking {
        val fake = RoutedFallback()
        val provider = IntervalMarketDataProvider(fake, 5, minimumBars = 3)
        val rows = provider.scan { _, _ -> }
        assertEquals(0, fake.bootstrapCalls)
        assertEquals("5m", rows.single().interval)
        assertFalse(rows.single().isRealtime)
        assertTrue(provider.scanDiagnostics().noDataSymbols.isEmpty())
    }

    @Test fun everyMissingSymbolRetainsItsActualReasonAndEarlierRunDoesNotLeak() = runBlocking {
        val fake = RoutedFallback(failures = true)
        val provider = IntervalMarketDataProvider(fake, 5, minimumBars = 3)
        assertEquals(listOf("GOOD"), provider.scan { _, _ -> }.map { it.symbol })
        val diagnostics = provider.scanDiagnostics()
        assertEquals(listOf("MISSING"), diagnostics.noDataSymbols)
        assertEquals(listOf("LIMITED"), diagnostics.errorSymbols)
        assertEquals("YAHOO_HTTP_404", diagnostics.reasonCodes["MISSING"])
        assertEquals("YAHOO_HTTP_403", diagnostics.reasonCodes["LIMITED"])
        assertFalse(diagnostics.noDataSymbols.contains("PREVIOUS_RUN"))
    }
}
