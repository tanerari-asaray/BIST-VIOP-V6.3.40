package tr.borsatakip.v5.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SharedPreferences/Keystore and readiness ownership; run only on an isolated test device. */
@RunWith(AndroidJUnit4::class)
class ConnectionLifecycleInstrumentedTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsStore
    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsStore(context)
        settings.baseUrl = "https://a.example"
        settings.apiKey = "synthetic_key_a"
        assertEquals("synthetic_key_a", settings.apiKey)
    }
    @After fun clean() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        ConnectionProbeStore.clear(context)
    }

    @Test fun concurrentConfigurationChangesNeverPairKeyWithAnotherOrigin() {
        val start = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<String>()
        val writer = Thread {
            try {
            start.await()
            repeat(10) {
                settings.baseUrl = "https://b.example"
                settings.apiKey = "synthetic_key_b"
                settings.baseUrl = "https://a.example"
                settings.apiKey = "synthetic_key_a"
            }
            } catch (e: Throwable) { failures.add("writer: ${e.javaClass.simpleName}") }
        }
        val reader = Thread {
            try {
            start.await()
            repeat(100) {
                val snapshot = settings.connectionSnapshot()
                if (snapshot.apiKey.isNotBlank()) {
                    val expected = if (snapshot.origin == "https://a.example") "synthetic_key_a" else "synthetic_key_b"
                    if (snapshot.apiKey != expected) failures.add(snapshot.origin)
                }
            }
            } catch (e: Throwable) { failures.add("reader: ${e.javaClass.simpleName}") }
        }
        writer.start(); reader.start(); start.countDown()
        writer.join(); reader.join()
        assertTrue("Mixed-origin snapshots: $failures", failures.isEmpty())
    }

    @Test fun oldSnapshotCannotPublishAfterKeyRotationOrOriginRoundTrip() {
        val old = settings.connectionSnapshot()
        settings.apiKey = "synthetic_rotated_key"
        assertFalse(settings.ifConnectionCurrent(old) { fail("Old key generation published") })
        val rotated = settings.connectionSnapshot()
        settings.baseUrl = "https://b.example"
        settings.baseUrl = "https://a.example"
        settings.apiKey = "synthetic_rotated_key"
        assertFalse(settings.isConnectionCurrent(rotated))
        assertTrue(settings.isConnectionCurrent(settings.connectionSnapshot()))
    }

    @Test fun cancelledTestReturnsToConfiguredAndCanBeTestedAgain() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val hanging = ProviderReadinessService(context) {
            entered.complete(Unit)
            awaitCancellation()
        }
        val job = launch(Dispatchers.Default) { hanging.test() }
        entered.await()
        assertEquals(ProviderState.PROVIDER_TESTING, hanging.localConfigState().state)
        job.cancelAndJoin()
        assertEquals(ProviderState.PROVIDER_CONFIGURED, hanging.localConfigState().state)
        val ready = ProviderReadinessService(context) { success("fresh") }.test()
        assertEquals(ProviderState.PROVIDER_READY, ready.state)
    }

    @Test fun oldTestCannotOverwriteNewConfigurationReadiness() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val oldService = ProviderReadinessService(context) {
            entered.complete(Unit)
            release.await()
            success("old")
        }
        val pending = async(Dispatchers.Default) { oldService.test() }
        entered.await()
        settings.baseUrl = "https://b.example"
        settings.apiKey = "synthetic_key_b"
        val fresh = ProviderReadinessService(context) { success("new") }.test()
        assertEquals(ProviderState.PROVIDER_READY, fresh.state)
        release.complete(Unit)
        pending.await()
        assertEquals("new", settings.lastProviderMessage)
        assertEquals("https://b.example", settings.connectionSnapshot().origin)
    }

    @Test fun persistedTestingWithoutLiveOwnerIsRecovered() {
        settings.lastProviderState = ProviderState.PROVIDER_TESTING.name
        val recovered = ProviderReadinessService(context).localConfigState()
        assertEquals(ProviderState.PROVIDER_CONFIGURED, recovered.state)
        assertFalse(settings.lastViopQuoteReady)
        assertEquals(0L, settings.lastBackendHealthAt)
    }

    private fun success(message: String) = BackendPreflightClient.Result(
        true, BackendPreflightClient.FailureKind.NONE, message,
        healthOk = true, authOk = true, viopContractsOk = true, viopQuoteOk = true)
}
