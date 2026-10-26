package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManualScanPipelineSourceTest {
    private fun mainRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(File(cwd, "src/main"), File(cwd, "app/src/main"), cwd.parentFile?.let { File(it, "app/src/main") })
            .filterNotNull().first { it.isDirectory }
    }
    private fun source(path: String) = File(mainRoot(), path).readText()

    @Test
    fun manualScanCannotBeSatisfiedByRealtimeSnapshotShortcut() {
        val src = source("java/tr/borsatakip/v5/ui/BistScanActivity.kt")
        assertFalse(src.contains("RealtimeScannerClient(this@BistScanActivity)"))
        assertTrue(src.contains("BackendPreflightClient(this@BistScanActivity).checkBist()"))
        assertTrue(src.contains("IntervalMarketDataProvider(baseProvider, analysisTimeframeMinutes)"))
        assertTrue(src.contains("BistScanner("))
    }

    @Test
    fun timeframeChangeInvalidatesOldResultsAndUiShowsFullPeriodContract() {
        val src = source("java/tr/borsatakip/v5/ui/BistScanActivity.kt")
        assertTrue(src.contains("AppSession.lastOpportunities = emptyList()"))
        assertTrue(src.contains("OHLCV: ${'$'}{tf.label}"))
        assertTrue(src.contains("Teknik analiz: ${'$'}{tf.label}"))
        assertTrue(src.contains("Momentum: ${'$'}{tf.label}"))
        assertTrue(src.contains("Kırılım: ${'$'}{tf.label}"))
    }

    @Test
    fun resultCardNamesItsOwnTimeframeOpportunity() {
        val src = source("java/tr/borsatakip/v5/ui/ScanResultsAdapter.kt")
        assertTrue(src.contains("${'$'}symbol — ${'$'}timeframeLabel FIRSAT"))
        assertTrue(src.contains("Timeframe: ${'$'}timeframeLabel"))
    }

    @Test
    fun productionUniverseFailsClosedAndDoesNotUseCachedUniverseAsScanSuccess() {
        val src = source("java/tr/borsatakip/v5/data/ProviderRouter.kt")
        assertTrue(src.contains("BIST HİSSE LİSTESİ ALINAMADI"))
        assertFalse(src.contains("return settings.cachedBistSymbols"))
    }

    @Test
    fun realtimeSnapshotDoesNotFakeDailyAs240Minutes() {
        val client = source("java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        val socket = source("java/tr/borsatakip/v5/data/RealtimeScannerSocket.kt")
        assertFalse(client.contains("coerceIn(1, 240)"))
        assertFalse(socket.contains("coerceIn(1, 240)"))
        assertTrue(client.contains("1 GÜN manuel gerçek günlük OHLCV"))
        assertTrue(socket.contains("240 DK olarak taklit etmez"))
    }
}
