package tr.borsatakip.v5.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutomaticScanSourceContractTest {
    private fun mainRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(File(cwd, "src/main"), File(cwd, "app/src/main"), cwd.parentFile?.let { File(it, "app/src/main") })
            .filterNotNull().first { it.isDirectory }
    }
    private fun source(path: String): String = File(mainRoot(), path).readText()

    @Test
    fun autoScanSettingIsPersistedSeparatelyFromAnalysisTimeframe() {
        val src = source("java/tr/borsatakip/v5/data/SettingsStore.kt")
        assertTrue(src.contains("auto_scan_enabled"))
        assertTrue(src.contains("analysis_timeframe_minutes"))
        assertTrue(src.contains("scan_cadence_minutes"))
    }

    @Test
    fun timeframeSelectionPreservesIndependentCadence() {
        val src = source("java/tr/borsatakip/v5/ui/BistScanActivity.kt")
        assertFalse(src.contains("settings.scanCadenceMinutes = tf.storedMinutes.coerceIn(1, 1440)"))
        assertTrue(src.contains("AutoScanScheduler.reconcile(this, allowForegroundStart = true)"))
    }

    @Test
    fun subFifteenMinuteAutomaticScanUsesForegroundService() {
        val src = source("java/tr/borsatakip/v5/worker/AutoScanScheduler.kt")
        assertTrue(src.contains("cadence < WORK_MANAGER_MINUTES"))
        assertTrue(src.contains("AutoScanForegroundService.start(app)"))
        assertTrue(src.contains("WORK_MANAGER_MINUTES = 15"))
    }

    @Test
    fun fifteenMinutesAndAboveUseWorkManager() {
        val src = source("java/tr/borsatakip/v5/worker/AutoScanScheduler.kt")
        assertTrue(src.contains("PeriodicWorkRequestBuilder<AutomaticScanWorker>"))
        assertTrue(src.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        assertTrue(src.contains("NetworkType.CONNECTED"))
    }

    @Test
    fun automaticForegroundServicePromotesBeforeStartingLoop() {
        val src = source("java/tr/borsatakip/v5/worker/AutoScanForegroundService.kt")
        val create = src.indexOf("override fun onCreate()")
        val promote = src.indexOf("promoteToForeground", create)
        val start = src.indexOf("override fun onStartCommand", create)
        assertTrue(create >= 0 && promote > create && promote < start)
        assertTrue(src.contains("ServiceCompat.startForeground"))
    }

    @Test
    fun manifestDeclaresAutomaticScanSpecialUseService() {
        val manifest = source("AndroidManifest.xml")
        assertTrue(manifest.contains(".worker.AutoScanForegroundService"))
        assertTrue(manifest.contains("foregroundServiceType=\"specialUse\""))
        assertTrue(manifest.contains("User-enabled periodic BIST market scanning"))
    }

    @Test
    fun backendMissingUsesExplicitDelayedFallbackNotUnsupportedTimeframe() {
        val src = source("java/tr/borsatakip/v5/ui/BistScanActivity.kt")
        assertFalse(src.contains("status.text = \"Periyot desteklenmiyor\""))
        assertFalse(src.contains("BACKEND_NOT_CONFIGURED"))
        assertFalse(src.contains("VERİ SERVİSİ KULLANILAMIYOR • Production Backend bağlantısı gerekli"))
        assertTrue(src.contains("Yahoo Finance • YEDEK/GECİKMELİ"))
        assertTrue(src.contains("BistScanMode.DELAYED_ANALYSIS"))
    }

    @Test
    fun selectedTimeframeProbeRunsBeforeFullUniverseScan() {
        val src = source("java/tr/borsatakip/v5/ui/BistScanActivity.kt")
        val probe = src.indexOf("scanProvider.fetchOne(sample)")
        val scanner = src.indexOf("val scanner = BistScanner(")
        assertTrue(probe >= 0 && scanner > probe)
        assertTrue(src.contains("ENDPOINT=history-window/${'$'}{timeframe.apiInterval}"))
    }

    @Test
    fun automaticRunnerUsesProductionOrExplicitDelayedProviderPipeline() {
        val src = source("java/tr/borsatakip/v5/worker/AutomaticScanRunner.kt")
        assertTrue(src.contains("if (productionReady) BackendPreflightClient(app).checkBist() else null"))
        assertTrue(src.contains("ProviderRouter(app, experimentalFallbackOverride = !productionReady)"))
        assertTrue(src.contains("IntervalMarketDataProvider(baseProvider, timeframe.storedMinutes)"))
        assertTrue(src.contains("if (productionReady) BistScanMode.REALTIME_ONLY else BistScanMode.DELAYED_ANALYSIS"))
        assertTrue(src.contains("BistScanner(provider, SignalHistoryRecorder(app), scanMode, \"AUTOMATIC\")"))
        assertTrue(src.contains("if (productionReady) LastSuccessfulScanStore(app).save(run, final.results)"))
        assertFalse(src.contains("mock", ignoreCase = true))
    }

    @Test
    fun tradingViewRemainsDisplayOnlyAndCannotBecomeMarketDataFallback() {
        val provider = source("java/tr/borsatakip/v5/data/ProviderRouter.kt")
        val settings = source("java/tr/borsatakip/v5/ui/SettingsActivity.kt")
        assertFalse(provider.contains("TradingView", ignoreCase = true))
        assertTrue(settings.contains("BIST/VİOP veri kaynağı: HAYIR"))
    }
}
