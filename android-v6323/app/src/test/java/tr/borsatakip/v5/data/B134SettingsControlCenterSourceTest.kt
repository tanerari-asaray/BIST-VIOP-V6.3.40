package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class B134SettingsControlCenterSourceTest {
    private fun mainRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(File(cwd, "src/main"), File(cwd, "app/src/main"), cwd.parentFile?.let { File(it, "app/src/main") })
            .filterNotNull().first { it.isDirectory }
    }

    private fun source(path: String): String = File(mainRoot(), path).readText()

    @Test fun dashboardRoutesEveryVisibleSettingsCardToRealDetailMode() {
        val activity = source("java/tr/borsatakip/v5/ui/SettingsActivity.kt")
        val layout = source("res/layout/activity_settings.xml")
        listOf(
            "systemStatusCard", "dataConnectionCard", "apiKeysCard", "scanSettingsCard",
            "tradeSimulationCard", "notificationsCard", "notificationHistoryCard", "appearanceCard",
            "localeCard", "diagnosticsCard", "aboutCard", "workModeCard"
        ).forEach { id ->
            assertTrue(layout.contains("@+id/$id"))
            assertTrue(activity.contains("R.id.$id"))
        }
    }

    @Test fun topActionsAreRealAndNotificationBadgeUsesPersistentAlertStore() {
        val activity = source("java/tr/borsatakip/v5/ui/SettingsActivity.kt")
        assertTrue(activity.contains("SettingsSearchActivity::class.java"))
        assertTrue(activity.contains("AlertEventStore(this).unreadCount()"))
        assertTrue(activity.contains("PopupMenu"))
        assertTrue(activity.contains("MODE_NOTIFICATION_HISTORY"))
    }

    @Test fun apiKeyIsNeverReloadedIntoEditableField() {
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        assertTrue(details.contains("addEdit(\"Yeni API Key\""))
        assertTrue(details.contains("settingsStore.apiKey = newKey"))
        assertFalse(details.contains("setText(settingsStore.apiKey)"))
        assertFalse(details.contains("InputType.TYPE_TEXT_VARIATION_PASSWORD, settingsStore.apiKey"))
    }

    @Test fun providerUrlValidationAndRealReadinessAreUsed() {
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        assertTrue(details.contains("ProviderReadinessService.isValidHttps(typed)"))
        assertTrue(details.contains("readiness.test()"))
        assertTrue(details.contains("BackendPreflightClient(this).checkBist()"))
        assertTrue(details.contains("IntervalMarketDataProvider(ProviderRouter(this, experimentalFallbackOverride = false)"))
    }

    @Test fun scanAndNotificationSettingsReconcileExistingSchedulersAndService() {
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        assertTrue(details.contains("AutoScanScheduler.reconcile"))
        assertTrue(details.contains("WorkScheduler.reconcile"))
        assertTrue(details.contains("RealtimeAlertService.start"))
        assertTrue(details.contains("RealtimeAlertService.stop"))
    }

    @Test fun quickMaintenancePreservesCredentialsAndUserStoresByContract() {
        val store = source("java/tr/borsatakip/v5/data/SettingsStore.kt")
        assertTrue(store.contains("fun clearTransientCaches()"))
        assertTrue(store.contains("fun resetOperationalSettingsPreservingCredentials()"))
        assertFalse(store.substringAfter("fun resetOperationalSettingsPreservingCredentials()").substringBefore("/** API anahtarı içermeyen").contains("clearApiKey()"))
    }

    @Test fun notificationHistoryUsesRealPersistentEventsAndReadTracking() {
        val store = source("java/tr/borsatakip/v5/data/AlertEventStore.kt")
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        assertTrue(store.contains("fun recentEvents"))
        assertTrue(store.contains("fun unreadCount"))
        assertTrue(store.contains("fun markAllRead"))
        assertTrue(details.contains("AlertEventStore(this)"))
        assertTrue(details.contains("Henüz bildirim bulunmuyor. Sahte örnek kayıt oluşturulmadı."))
    }

    @Test fun simulationUsesTradeForwardEngineAndNeverSendsOrder() {
        val details = source("java/tr/borsatakip/v5/ui/SettingsDetailActivity.kt")
        assertTrue(details.contains("TradeForwardEngine.estimateRoundTrip"))
        assertTrue(details.contains("Bu sonuç simülasyondur; emir gönderilmedi."))
        assertFalse(details.contains("placeOrder"))
        assertFalse(details.contains("sendOrder"))
    }

    @Test fun manifestRegistersOnlyRealNewSettingsScreens() {
        val manifest = source("AndroidManifest.xml")
        assertTrue(manifest.contains(".ui.SettingsDetailActivity"))
        assertTrue(manifest.contains(".ui.SettingsSearchActivity"))
    }
}
