package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class B135SettingsRegressionSourceTest {
    private fun mainRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(File(cwd, "src/main"), File(cwd, "app/src/main"), cwd.parentFile?.let { File(it, "app/src/main") })
            .filterNotNull().first { it.isDirectory }
    }

    private fun source(path: String): String = File(mainRoot(), path).readText()

    @Test fun deniedNotificationPermissionResyncsVisibleToggle() {
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        assertTrue(details.contains("notificationToggle?.isChecked = settingsStore.notifications"))
    }

    @Test fun diagnosticsPreserveCoroutineCancellation() {
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        val settings = source("java/tr/borsatakip/v5/ui/SettingsActivity.kt")
        listOf(details, settings).forEach { text ->
            assertTrue(text.contains("catch (ce: CancellationException)"))
            assertTrue(text.contains("throw ce"))
        }
        assertFalse(details.contains("runCatching { provider.fetchOne(requireNotNull(preflight.sampleSymbol)) }.getOrNull()"))
        assertFalse(settings.contains("runCatching { provider.fetchOne(sample) }.getOrNull()"))
    }

    @Test fun backendOriginChangeWarnsBeforeCredentialRemoval() {
        val store = source("java/tr/borsatakip/v5/data/SettingsStore.kt")
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        assertTrue(store.contains("fun willClearApiKeyOnBackendChange"))
        assertTrue(details.contains("Backend sunucusu değiştiği için mevcut API anahtarı güvenlik amacıyla kaldırılacaktır"))
    }

    @Test fun mtfOnlyCacheIsReportedAsCleared() {
        val cache = source("java/tr/borsatakip/v5/data/MtfHistoryCache.kt")
        val store = source("java/tr/borsatakip/v5/data/SettingsStore.kt")
        assertTrue(cache.contains("fun hasEntries(): Boolean = entries.isNotEmpty()"))
        assertTrue(store.contains("hadSettingsCache || hadNewsCache || hadMtfCache"))
    }

    @Test fun zeroProviderTimestampIsRenderedAsYok() {
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        assertTrue(details.contains("private fun formatTime(epoch: Long): String = if (epoch > 0L)"))
        assertTrue(details.contains("} else \"yok\""))
    }
}
