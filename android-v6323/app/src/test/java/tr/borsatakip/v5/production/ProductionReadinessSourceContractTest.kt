package tr.borsatakip.v5.production

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductionReadinessSourceContractTest {
    private val root = sequenceOf(File("."), File(".."), File("../.."), File("../../.."))
        .map { it.canonicalFile }
        .first { File(it, "app/src/main").exists() }
    private fun text(path: String) = File(root, path).readText()

    @Test fun bootReceiverRestoresSchedulerContract() {
        val src = text("app/src/main/java/tr/borsatakip/v5/worker/BootCompletedReceiver.kt")
        val manifest = text("app/src/main/AndroidManifest.xml")
        assertTrue(src.contains("ACTION_BOOT_COMPLETED"))
        assertTrue(src.contains("ACTION_MY_PACKAGE_REPLACED"))
        assertTrue(src.contains("AutoScanScheduler.reconcile(context, allowForegroundStart = false)"))
        assertTrue(manifest.contains("RECEIVE_BOOT_COMPLETED"))
        assertTrue(manifest.contains(".worker.BootCompletedReceiver"))
    }

    @Test fun sub15BootDoesNotPretendForegroundRestartSucceeded() {
        val src = text("app/src/main/java/tr/borsatakip/v5/worker/BootCompletedReceiver.kt")
        assertTrue(src.contains("WAITING_FOR_APP_START_AFTER_BOOT"))
        assertFalse(src.contains("allowForegroundStart = true"))
    }

    @Test fun runtimeDiagnosticsNeverExposeApiKey() {
        val src = text("app/src/main/java/tr/borsatakip/v5/data/RuntimeReadinessDiagnostics.kt")
        assertTrue(src.contains("apiKeyConfigured"))
        assertFalse(src.contains("apiKey ="))
        assertTrue(src.contains("sanitizedOrigin"))
    }

    @Test fun diagnosticsCheckDevicePowerNotificationAndTradingViewHandler() {
        val src = text("app/src/main/java/tr/borsatakip/v5/data/RuntimeReadinessDiagnostics.kt")
        assertTrue(src.contains("isIgnoringBatteryOptimizations"))
        assertTrue(src.contains("POST_NOTIFICATIONS"))
        assertTrue(src.contains("resolveActivity"))
        assertTrue(src.contains("BIST:KIMMR"))
    }

    @Test fun automaticScanRecordsSoakHealthCounters() {
        val settings = text("app/src/main/java/tr/borsatakip/v5/data/SettingsStore.kt")
        val runner = text("app/src/main/java/tr/borsatakip/v5/worker/AutomaticScanRunner.kt")
        listOf("autoScanSuccessfulRuns", "autoScanFailedRuns", "autoScanConsecutiveFailures", "autoScanLastDurationMs", "autoScanLastHeartbeatAt").forEach {
            assertTrue(settings.contains(it))
            assertTrue(runner.contains(it))
        }
    }

    @Test fun productionReadinessProbeUsesSelectedTimeframeAndRealProvider() {
        val src = text("app/src/main/java/tr/borsatakip/v5/ui/SettingsActivity.kt")
        assertTrue(src.contains("BackendPreflightClient(this).checkBist()"))
        assertTrue(src.contains("IntervalMarketDataProvider(ProviderRouter(this, experimentalFallbackOverride = false), tf.storedMinutes)"))
        assertTrue(src.contains("RealTimeIntegrityPolicy.MIN_HISTORY_BARS"))
        assertTrue(src.contains("24/48/72"))
    }

    @Test fun releaseBuildRequiresExternalSigningSecrets() {
        val gradle = text("app/build.gradle.kts")
        assertTrue(gradle.contains("BORSA_KEYSTORE_PATH"))
        assertTrue(gradle.contains("BORSA_STORE_PASSWORD"))
        assertTrue(gradle.contains("BORSA_KEY_ALIAS"))
        assertTrue(gradle.contains("BORSA_KEY_PASSWORD"))
        assertTrue(gradle.contains("check(hasReleaseSigning)"))
    }

    @Test fun compilerWarningsAreBuildBlocking() {
        val gradle = text("app/build.gradle.kts")
        assertTrue(gradle.contains("allWarningsAsErrors = true"))
        assertTrue(gradle.contains("\"-Werror\""))
    }
}
