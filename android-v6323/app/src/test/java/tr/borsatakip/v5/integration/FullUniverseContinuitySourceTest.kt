package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression checks for the screen-navigation cancellation reported in Build 644. */
class FullUniverseContinuitySourceTest {
    private val screen by lazy { File("src/main/java/tr/borsatakip/v5/ui/FullUniverseScanActivity.kt").readText() }
    private val runtime by lazy { File("src/main/java/tr/borsatakip/v5/ui/FullUniverseScanRuntime.kt").readText() }

    @Test fun scanIsOwnedByApplicationProcessNotActivityLifecycle() {
        assertTrue(runtime.contains("CoroutineScope(SupervisorJob()+Dispatchers.IO)"))
        assertTrue(runtime.contains("inputContext.applicationContext"))
        assertTrue(screen.contains("UniverseObservationService.start(this, localMode, resume, retryFailuresOnly)"))
        assertTrue(screen.contains("retryFailuresOnly"))
        assertFalse(screen.contains("job?.cancel()"))
        assertFalse(screen.contains("lifecycleScope.launch {\n            try {\n                val stockProvider"))
    }

    @Test fun userStopAndUnexplainedCancellationAreDistinguished() {
        assertTrue(runtime.contains("KULLANICI_TARAFINDAN_DURDURULDU"))
        assertTrue(runtime.contains("BEKLENMEYEN_IPTAL"))
        assertTrue(screen.contains("FullUniverseScanRuntime.cancel()"))
    }

    @Test fun interruptedProcessCannotBeMisreportedAsComplete() {
        assertTrue(screen.contains("ÖNCEKİ UYGULAMA SÜRECİ KESİLDİ"))
        assertTrue(screen.contains("FullUniversePolicy.State.PARTIAL"))
        // Build 660: local BIST coverage must remain partial for the combined market screen.
        assertTrue(runtime.contains("localMode -> FullUniversePolicy.State.PARTIAL"))
        assertTrue(runtime.contains("counts.triedBist != counts.discoveredBist"))
    }

    @Test fun coldStartCorrectsPersistedRunningDiagnostics() {
        val app = File("src/main/java/tr/borsatakip/v5/BorsaTakipApp.kt").readText()
        val store = File("src/main/java/tr/borsatakip/v5/data/FullUniverseScanSnapshotStore.kt").readText()
        assertTrue(app.contains("FullUniverseScanSnapshotStore(this).recoverInterruptedProcess()"))
        assertTrue(store.contains("previous.status != FullUniversePolicy.State.RUNNING.name"))
        assertTrue(store.contains("FullUniversePolicy.State.PARTIAL.name"))
        assertTrue(store.contains("ÖNCEKİ UYGULAMA SÜRECİ KESİLDİ"))
    }

    @Test fun observationKeepsTradingSafetyBoundary() {
        assertTrue(runtime.contains("YahooFallbackProvider(context)"))
        assertTrue(runtime.contains("ProviderRouter(context,experimentalFallbackOverride=false,connectionOverride=connection)"))
        assertTrue(runtime.contains("canlı işlem sinyali değildir"))
        assertTrue(runtime.contains("FullUniverseScanSnapshotStore(context)"))
    }
}

