package tr.borsatakip.v5.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.CompletableDeferred
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.R
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.model.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Offline fixtures, real Espresso touches, initialized destinations and asynchronous request failures. */
@RunWith(AndroidJUnit4::class)
class ViopAnalysisRuntimeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var preferences: Map<String, *>
    private var selected: ViopContract? = null
    private var opportunity: ViopOpportunity? = null

    @Before fun isolate() {
        preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toMap()
        selected = AppSession.selectedViopContract
        opportunity = AppSession.selectedViopOpportunity
        SettingsStore(context).baseUrl = ""
    }

    @After fun restore() {
        inst.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() }
        }
        inst.waitForIdleSync()
        AppSession.selectedViopContract = selected
        AppSession.selectedViopOpportunity = opportunity
        val edit = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear()
        preferences.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Float -> edit.putFloat(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        assertTrue(edit.commit())
    }

    private fun fixtures(): List<ViopContract> = (1..32).map { i ->
        ViopContract("F_TEST${i}_1299", "TEST$i", if (i % 2 == 0) "2099-12" else "2099-11",
            providerId = "backend", providerLabel = "TEST VERİSİ", validity = SignalValidity.WATCH,
            lastTradingAt = 4102444800000L, expiryAt = 4102444800000L)
    } + ViopContract("O_TESTX1299C100", "TESTX", "2099-12", contractType = "Opsiyon",
        providerId = "backend", providerLabel = "TEST VERİSİ", validity = SignalValidity.INSUFFICIENT)

    private fun load(scene: ActivityScenario<ViopAnalysisActivity>, loader: suspend () -> Result<List<ViopContract>>) {
        scene.onActivity { a ->
            a.javaClass.getDeclaredField("catalogLoader").apply { isAccessible = true }.set(a, loader)
            SettingsStore(context).baseUrl = "https://fixture.invalid"
            a.javaClass.getDeclaredMethod("loadCatalog").apply { isAccessible = true }.invoke(a)
        }
    }
    private fun waitFor(scene: ActivityScenario<ViopAnalysisActivity>, predicate: (ViopAnalysisActivity) -> Boolean) {
        repeat(100) {
            var done = false
            scene.onActivity { done = predicate(it) }
            if (done) { inst.waitForIdleSync(); return }
            SystemClock.sleep(50)
        }
        error("Analysis touch did not produce expected state")
    }
    private fun count(scene: ActivityScenario<ViopAnalysisActivity>, value: Int) = waitFor(scene) {
        it.findViewById<RecyclerView>(R.id.analysisList).adapter?.itemCount == value &&
            it.findViewById<View>(R.id.analysisProgress).visibility == View.GONE
    }
    private fun awaitActivity(type: Class<out Activity>) {
        repeat(100) {
            var active = false
            inst.runOnMainSync { active = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { type.isInstance(it) && it.window.decorView.hasWindowFocus() } }
            if (active) { inst.waitForIdleSync(); return }
            SystemClock.sleep(50)
        }
        error("Missing initialized ${type.simpleName}")
    }
    private fun capture(name: String) {
        // Earlier settings tests copy diagnostics. Let Android's transient clipboard
        // overlay expire before photographing the app; do not crop or edit evidence.
        inst.waitForIdleSync(); SystemClock.sleep(7000)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("No analysis screenshot")
        val file = File(context.filesDir, "build660-screens/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val streams = inst.uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(streams[1]).use { it.write((
            "mkdir -p /data/local/tmp/build660-screens && run-as ${context.packageName} cat files/build660-screens/$name.png > /data/local/tmp/build660-screens/$name.png && echo EXPORTED\n").toByteArray()) }
        assertEquals("EXPORTED", ParcelFileDescriptor.AutoCloseInputStream(streams[0]).bufferedReader().use { it.readText().trim() })
    }

    @Test fun noConnectionShowsNoUnderlyingCatalogAndSettingsActionWorks() {
        ActivityScenario.launch(ViopAnalysisActivity::class.java).use { scene ->
            count(scene, 0)
            onView(withId(R.id.analysisEmptyTitle)).check(matches(withText("VİOP bağlantısı gerekli")))
            capture("25-viop-analysis-no-connection")
            onView(withId(R.id.analysisEmptyAction)).perform(click())
            awaitActivity(SettingsActivity::class.java)
            pressBack(); awaitActivity(ViopAnalysisActivity::class.java)
            count(scene, 0)
        }
    }

    @Test fun allProviderContractsAllMaturitiesSearchKindExpiryAndResetWorkWithTouch() {
        ActivityScenario.launch(ViopAnalysisActivity::class.java).use { scene ->
            load(scene) { Result.success(fixtures()) }; count(scene, 33)
            onView(withId(R.id.analysisCount)).check(matches(withText("33 / 33 sözleşme")))
            capture("26-viop-analysis-all-contracts-fixture")
            onView(withId(R.id.analysisFutures)).perform(click()); count(scene, 32)
            onView(withId(R.id.analysisExpiry)).perform(click())
            onData(equalTo("2099-12")).perform(click()); count(scene, 16)
            onView(withId(R.id.analysisOptions)).perform(click()); count(scene, 1)
            onView(withId(R.id.analysisSymbol)).check(matches(withText("O_TESTX1299C100")))
            onView(withId(R.id.analysisSearch)).perform(replaceText("NO_MATCH")); closeSoftKeyboard()
            count(scene, 0)
            onView(withId(R.id.analysisEmptyAction)).perform(click()); count(scene, 33)
            onView(withId(R.id.analysisSearch)).perform(replaceText("F_TEST32_1299")); closeSoftKeyboard()
            count(scene, 1)
            onView(withId(R.id.analysisSymbol)).check(matches(withText("F_TEST32_1299")))
            onView(withId(R.id.analysisClear)).perform(click()); count(scene, 33)
        }
    }

    @Test fun refreshClearsOldUniverseBlocksDuplicateAndRecoversFromFailureWithTouch() {
        ActivityScenario.launch(ViopAnalysisActivity::class.java).use { scene ->
            load(scene) { Result.success(fixtures()) }; count(scene, 33)
            val gate = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            load(scene) { calls.incrementAndGet(); gate.await(); Result.failure(IllegalStateException("TEST offline")) }
            waitFor(scene) { !it.findViewById<View>(R.id.analysisRefresh).isEnabled }
            scene.onActivity { a -> assertEquals(0, a.findViewById<RecyclerView>(R.id.analysisList).adapter!!.itemCount) }
            onView(withId(R.id.analysisRefresh)).perform(click())
            assertEquals(1, calls.get())
            gate.complete(Unit); count(scene, 0)
            onView(withId(R.id.analysisEmptyMessage)).check(matches(withText(org.hamcrest.Matchers.containsString("Önceki liste temizlendi"))))
            capture("27-viop-analysis-fetch-error-cleared")
            scene.onActivity { a ->
                val success: suspend () -> Result<List<ViopContract>> = { Result.success(fixtures().take(2)) }
                a.javaClass.getDeclaredField("catalogLoader").apply { isAccessible = true }.set(a, success)
            }
            onView(withId(R.id.analysisEmptyAction)).perform(click()); count(scene, 2)
            onView(withId(R.id.analysisCount)).check(matches(withText("2 / 2 sözleşme")))
        }
    }

    @Test fun selectionOpensExactMaturityAndDiscardsOldOpportunity() {
        val old = fixtures()[0]
        val technical = TechnicalSnapshot(10.1, 10.0, 9.5, 55.0, 0.1, 0.08, 11.0, 9.0, 0.3, 10.1, 1.2, 9.0, 11.0)
        val quote = ViopQuote(old.symbol, 999.0, null, null, null, null, null, 0L, 0L, "TEST", false, null, false)
        AppSession.selectedViopContract = old
        AppSession.selectedViopOpportunity = ViopOpportunity(old, quote, emptyList(), technical, 0, 0, 0, 0, 0, 0, 0,
            "NONE", "TEST", SignalValidity.WATCH, 0L, 0)
        ActivityScenario.launch(ViopAnalysisActivity::class.java).use { scene ->
            load(scene) { Result.success(fixtures()) }; count(scene, 33)
            onView(withId(R.id.analysisSearch)).perform(replaceText("F_TEST32_1299")); closeSoftKeyboard()
            count(scene, 1)
            onView(withId(R.id.analysisSymbol)).perform(click())
            awaitActivity(ViopDetailActivity::class.java)
            assertEquals("F_TEST32_1299", AppSession.selectedViopContract?.symbol)
            assertEquals("2099-12", AppSession.selectedViopContract?.expiry)
            assertNull(AppSession.selectedViopOpportunity)
            pressBack(); awaitActivity(ViopAnalysisActivity::class.java)
            count(scene, 1)
        }
    }
}
