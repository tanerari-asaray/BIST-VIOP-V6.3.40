package tr.borsatakip.v5.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.ViopUnderlyingScanner
import tr.borsatakip.v5.data.MarketDataProvider
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.favorites.FavoriteRepository
import tr.borsatakip.v5.model.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Actual injected touch events. Destinations are allowed to initialize; no blocking ActivityMonitor. */
@RunWith(AndroidJUnit4::class)
class MarketTouchRuntimeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var preferences: Map<String, *>
    private lateinit var previous: List<Opportunity>
    private var selected: Opportunity? = null
    private var selectedContract: ViopContract? = null
    private var selectedViop: ViopOpportunity? = null
    private val technical = TechnicalSnapshot(10.1, 10.0, 9.5, 55.0, 0.1, 0.08, 11.0, 9.0, 0.3, 10.1, 1.2, 9.0, 11.0)

    @Before fun isolate() {
        preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toMap()
        previous = AppSession.lastOpportunities
        selected = AppSession.selected
        selectedContract = AppSession.selectedViopContract
        selectedViop = AppSession.selectedViopOpportunity
        SettingsStore(context).apply {
            baseUrl = "https://fixture.invalid"
            apiKey = "offline-touch-test-key"
            lastProviderState = "PROVIDER_TESTING"
            experimentalProvidersEnabled = false
            yahooFallbackEnabled = false
        }
        AppSession.lastOpportunities = fixtures()
    }

    @After fun restore() {
        inst.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .toList().forEach { it.finish() }
        }
        inst.waitForIdleSync()
        AppSession.lastOpportunities = previous
        AppSession.selected = selected
        AppSession.selectedViopContract = selectedContract
        AppSession.selectedViopOpportunity = selectedViop
        val edit = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear()
        preferences.forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue(edit.commit())
    }

    private fun fixture(symbol: String, direction: String, live: Boolean = false) = Opportunity(
        symbol = symbol, companyName = "TEST VERİSİ · $symbol", price = 10.25, dailyChangePct = -1.2,
        score = 80, riskScore = 20, direction = direction, technicalLabel = "TEST", volumeLabel = "TEST",
        kapLabel = "TEST", liquidityLabel = "TEST", support = 9.0, resistance = 11.0, source = "TEST VERİSİ",
        dataTimestamp = System.currentTimeMillis() - if (live) 1000 else 900000,
        candles = (0 until 35).map { i -> Candle(System.currentTimeMillis() - (36L - i) * 300000,
            10.0 + i * 0.01, 10.2 + i * 0.01, 9.8 + i * 0.01, 10.1 + i * 0.01, 1000.0 + i) },
        technical = technical, isRealtime = live, dataMode = if (live) DataMode.REALTIME else DataMode.DELAYED,
        signalValidity = if (live) SignalValidity.VALID else SignalValidity.WATCH,
        decisionState = if (live) DecisionState.VERIFIED_OPPORTUNITY else DecisionState.WATCH)

    private fun fixtures() = listOf(fixture("TESTZ", "NONE"), fixture("TESTB", "LONG", true),
        fixture("TESTA", "SHORT", true), fixture("TESTC", "LONG"))

    private fun set(a: Activity, field: String, value: Any) {
        a.javaClass.getDeclaredField(field).apply { isAccessible = true }.set(a, value)
    }
    private fun call(a: Activity, method: String) {
        a.javaClass.getDeclaredMethod(method).apply { isAccessible = true }.invoke(a)
    }
    private fun <A : Activity> waitFor(scene: ActivityScenario<A>, predicate: (A) -> Boolean) {
        repeat(80) {
            var done = false
            scene.onActivity { done = predicate(it) }
            if (done) { inst.waitForIdleSync(); return }
            SystemClock.sleep(50)
        }
        error("Touch action did not produce its expected visible result")
    }
    private fun awaitActivity(type: Class<out Activity>) {
        WindowFocusAwaiter.await(type)
    }
    private fun navigate(id: Int, target: Class<out Activity>, source: Class<out Activity>) {
        if (id == R.id.stocksEmptyAction) onView(withId(id)).perform(scrollTo(), click())
        else onView(withId(id)).perform(click())
        awaitActivity(target)
        pressBack()
        awaitActivity(source)
    }
    private fun <A : Activity> expand(scene: ActivityScenario<A>) {
        scene.onActivity { it.findViewById<AppBarLayout>(R.id.marketAppBar).setExpanded(true, false) }
        inst.waitForIdleSync()
    }
    // A user can scroll the collapsing header away before touching a list row.
    // Keep Espresso's standard click visibility constraints intact.
    private fun revealStockRow(scene: ActivityScenario<StocksActivity>) {
        scene.onActivity { activity ->
            activity.findViewById<AppBarLayout>(R.id.marketAppBar).setExpanded(false, false)
            activity.findViewById<RecyclerView>(R.id.stocksList).scrollToPosition(0)
        }
        inst.waitForIdleSync()
        waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList)
            .findViewHolderForAdapterPosition(0) != null }
    }

    private fun inject(a: ViopActivity) {
        a.lifecycleScope.coroutineContext.cancelChildren()
        set(a, "providerAutoTestInFlight", true)
        set(a, "underlyingScanCompleted", true)
        SettingsStore(context).baseUrl = ""
        set(a, "underlyingItems", fixtures().map { ViopUnderlyingScanner.Candidate(
            ViopContract("F_${it.symbol}1299", it.symbol, "2099-12"), it) })
        set(a, "showingUnderlying", true)
        call(a, "applyDashboardFilter")
        call(a, "refreshProviderState")
    }

    private fun capture(name: String, scene: ActivityScenario<out Activity>) {
        scene.onActivity { a ->
            a.window.decorView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            a.window.decorView.invalidate()
        }
        inst.waitForIdleSync()
        SystemClock.sleep(400)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("Missing touch evidence screenshot")
        val file = File(context.filesDir, "build670-screens/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        scene.onActivity { it.window.decorView.setLayerType(View.LAYER_TYPE_NONE, null) }
    }

    @Test fun stocksHeaderButtonsOpenRealDestinationsWithTouch() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(Activity::class.java)
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 4 }
            for ((id, type) in listOf(R.id.stocksFavorites to FavoritesActivity::class.java,
                R.id.stocksSettings to SettingsActivity::class.java, R.id.stocksNotifications to NotificationsActivity::class.java,
                R.id.stocksScanAction to BistScanActivity::class.java, R.id.stocksScanCard to BistScanActivity::class.java)) {
                expand(scene)
                navigate(id, type, StocksActivity::class.java)
            }
        }
    }

    @Test fun stocksTouchFiltersSearchSortFavoriteAndDetail() {
        val repo = FavoriteRepository.get(context)
        val wasFavorite = runBlocking { repo.isFavorite("TESTZ") }
        try {
            ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(Activity::class.java)
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 4 }
                waitFor(scene) { it.window.decorView.hasWindowFocus() }
                onView(withId(R.id.stocksFilterLong)).perform(click())
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).let { rows -> rows.adapter?.itemCount == 1 &&
                    rows.findViewHolderForAdapterPosition(0)?.itemView?.findViewById<TextView>(R.id.stockSymbol)?.text == "TESTB" } }
                onView(withId(R.id.stockSymbol)).check(matches(withText("TESTB")))
                onView(withId(R.id.stocksFilterShort)).perform(scrollTo(), click())
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).let { rows -> rows.adapter?.itemCount == 1 &&
                    rows.findViewHolderForAdapterPosition(0)?.itemView?.findViewById<TextView>(R.id.stockSymbol)?.text == "TESTA" } }
                onView(withId(R.id.stockSymbol)).check(matches(withText("TESTA")))
                onView(withId(R.id.stocksFilterAll)).perform(scrollTo(), click())
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 4 }
                onView(withId(R.id.stocksSort)).perform(click())
                onView(withText("Sembol A–Z")).perform(click())
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).findViewHolderForAdapterPosition(0)
                    ?.itemView?.findViewById<TextView>(R.id.stockSymbol)?.text == "TESTA" }
                onView(withId(R.id.stocksSearch)).perform(replaceText("NO_MATCH"))
                closeSoftKeyboard()
                waitFor(scene) { it.findViewById<View>(R.id.stocksEmptyState).isShown }
                onView(withId(R.id.stocksEmptyAction)).perform(scrollTo(), click())
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 4 }
                onView(withId(R.id.stocksSearch)).perform(replaceText("TESTZ"))
                closeSoftKeyboard()
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 1 }
                revealStockRow(scene)
                onView(withId(R.id.stockFavorite)).check(matches(isCompletelyDisplayed())).perform(click())
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).findViewHolderForAdapterPosition(0)
                    ?.itemView?.findViewById<TextView>(R.id.stockFavorite)?.text == if (wasFavorite) "☆" else "★" }
                assertEquals(!wasFavorite, runBlocking { repo.isFavorite("TESTZ") })
                capture("21-stocks-touch-result", scene)
                scene.recreate()
                WindowFocusAwaiter.await(StocksActivity::class.java)
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 1 }
                revealStockRow(scene)
                onView(withId(R.id.stockFavorite)).check(matches(withText(if (wasFavorite) "☆" else "★")))
                onView(withId(R.id.stockMore)).check(matches(isCompletelyDisplayed()))
                onView(withId(R.id.stockMore)).perform(click())
                awaitActivity(StockDetailActivity::class.java)
                assertEquals("TESTZ", AppSession.selected?.symbol)
                pressBack()
                awaitActivity(StocksActivity::class.java)
            }
        } finally {
            runBlocking { if (wasFavorite) repo.add("TESTZ", "TEST VERİSİ") else repo.remove("TESTZ") }
        }
    }

    @Test fun stocksUnavailableFiltersGiveVisibleReasonsWithoutChangingRows() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(Activity::class.java)
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 4 }
            for ((id, label) in listOf(R.id.stocksFilterBist30 to "BIST 30", R.id.stocksFilterBank to "Bankacılık",
                R.id.stocksFilterIndustry to "Sanayi", R.id.stocksFilterTech to "Teknoloji")) {
                onView(withId(id)).perform(scrollTo(), click())
                onView(withText("$label filtresi kullanılamıyor")).check(matches(isDisplayed()))
                onView(withId(android.R.id.button1)).perform(click())
                scene.onActivity { assertEquals(4, it.findViewById<RecyclerView>(R.id.stocksList).adapter!!.itemCount) }
            }
        }
    }

    @Test fun stocksEmptyScanAndBottomNavigationReceiveTouch() {
        AppSession.lastOpportunities = emptyList()
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(Activity::class.java)
            waitFor(scene) { it.findViewById<View>(R.id.stocksEmptyState).isShown }
            navigate(R.id.stocksEmptyAction, BistScanActivity::class.java, StocksActivity::class.java)
            navigate(R.id.navViop, ViopActivity::class.java, StocksActivity::class.java)
            navigate(R.id.navFav, FavoritesActivity::class.java, StocksActivity::class.java)
            navigate(R.id.navSettings, SettingsActivity::class.java, StocksActivity::class.java)
            navigate(R.id.navHome, MainActivity::class.java, StocksActivity::class.java)
            onView(withId(R.id.navBist)).perform(click())
            awaitActivity(StocksActivity::class.java)
        }
    }

    @Test fun viopHeaderButtonsOpenRealDestinationsWithTouch() {
        ActivityScenario.launch(ViopActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(Activity::class.java)
            scene.onActivity { inject(it) }
            for ((id, type) in listOf(R.id.openFavorites to FavoritesActivity::class.java,
                R.id.openSettings to SettingsActivity::class.java, R.id.openNotifications to NotificationsActivity::class.java,
                R.id.openContracts to ViopContractsActivity::class.java, R.id.summaryWatch to ViopContractsActivity::class.java)) {
                expand(scene)
                navigate(id, type, ViopActivity::class.java)
            }
        }
    }

    @Test fun viopTouchFiltersSearchSortAndContractAnalysis() {
        ActivityScenario.launch(ViopActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(Activity::class.java)
            scene.onActivity { inject(it) }
            onView(withId(R.id.summaryLong)).perform(click())
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.list).adapter?.itemCount == 2 }
            onView(withId(R.id.summaryShort)).perform(click())
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.list).adapter?.itemCount == 1 }
            onView(withId(R.id.filterAll)).perform(click())
            for (id in listOf(R.id.filterIndex, R.id.filterFx, R.id.filterCommodity, R.id.filterRate)) {
                onView(withId(id)).perform(scrollTo(), click())
                waitFor(scene) { it.findViewById<View>(R.id.viopEmptyState).isShown }
                onView(withId(R.id.viopEmptyAction)).perform(click())
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.list).adapter?.itemCount == 4 }
            }
            onView(withId(R.id.viopSort)).perform(click())
            onView(withText("Sembol A–Z")).perform(click())
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.list).findViewHolderForAdapterPosition(0)
                ?.itemView?.findViewById<TextView>(R.id.viopSymbol)?.text == "TESTA" }
            onView(withId(R.id.viopSearch)).perform(replaceText("TESTZ"))
            closeSoftKeyboard()
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.list).adapter?.itemCount == 1 }
            onView(withId(R.id.openSignals)).perform(click())
            onView(withText("Doğrulanmış VİOP sinyali yok")).check(matches(isDisplayed()))
            onView(withId(android.R.id.message)).check(matches(withText(org.hamcrest.Matchers.containsString("VİOP sinyali üretmez"))))
            capture("22-viop-touch-signals-unavailable", scene)
            onView(withId(android.R.id.button2)).perform(click())
            onView(withId(R.id.openAnalysis)).perform(click())
            awaitActivity(ViopAnalysisActivity::class.java)
            onView(withId(R.id.analysisEmptyTitle)).check(matches(withText("VİOP bağlantısı gerekli")))
            onView(withId(R.id.analysisCount)).check(matches(withText("0 / 0 sözleşme")))
            pressBack()
            awaitActivity(ViopActivity::class.java)
        }
    }

    private class GatedProvider : MarketDataProvider {
        override val id = "offline-touch-fixture"
        override val displayName = "TEST VERİSİ"
        @Volatile var gate = CompletableDeferred<Unit>()
        @Volatile var fail = false
        val calls = AtomicInteger()
        override suspend fun scan(onProgress: (Int, Int) -> Unit): List<Stock> = emptyList()
        override suspend fun fetchOne(symbol: String): Stock? {
            calls.incrementAndGet()
            gate.await()
            if (fail) error("TEST: upstream unavailable")
            return null
        }
    }

    @Test fun viopRefreshShowsBusyRejectsDuplicatesAndRecoversForRetry() {
        val provider = GatedProvider()
        ActivityScenario.launch(ViopActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(Activity::class.java)
            scene.onActivity { a -> inject(a); set(a, "underlyingScanner", ViopUnderlyingScanner(provider)) }
            onView(withId(R.id.refresh)).perform(click())
            waitFor(scene) { !it.findViewById<Button>(R.id.refresh).isEnabled && provider.calls.get() == 4 }
            onView(withId(R.id.refresh)).check(matches(withText("Taranıyor…")))
            onView(withId(R.id.viopScanProgress)).check(matches(isDisplayed()))
            scene.onActivity { call(it, "refreshProviderState") }
            onView(withId(R.id.refresh)).check(matches(org.hamcrest.Matchers.not(isEnabled())))
            onView(withId(R.id.viopScanCard)).perform(click())
            assertEquals("No second scan may enter the provider", 4, provider.calls.get())
            capture("23-viop-touch-scan-busy", scene)
            provider.gate.complete(Unit)
            waitFor(scene) { it.findViewById<Button>(R.id.refresh).isEnabled &&
                it.findViewById<TextView>(R.id.status).text.contains("NO_STOCK_DATA") }
            val attempted = provider.calls.get()
            assertTrue(attempted > 4)
            onView(withId(R.id.refresh)).check(matches(withText("Yenile  ›")))
            scene.onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.viopScanProgress).visibility) }
            provider.gate = CompletableDeferred()
            provider.fail = true
            onView(withId(R.id.refresh)).perform(click())
            waitFor(scene) { !it.findViewById<Button>(R.id.refresh).isEnabled && provider.calls.get() == attempted + 4 }
            provider.gate.complete(Unit)
            waitFor(scene) { it.findViewById<Button>(R.id.refresh).isEnabled &&
                it.findViewById<TextView>(R.id.status).text.contains("FETCH_ERROR") }
            assertEquals(attempted * 2, provider.calls.get())
            capture("24-viop-touch-retry-complete", scene)
        }
    }

    @Test fun viopAnalysisClearsOldContractAndOffersWorkingSettingsAction() {
        ActivityScenario.launch(ViopActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(Activity::class.java)
            scene.onActivity { a ->
                inject(a)
                set(a, "underlyingItems", emptyList<ViopUnderlyingScanner.Candidate>())
                set(a, "previewContract", ViopContract("F_OLD1226", "OLD", "2026-12"))
                call(a, "clearPreview")
                assertNull(a.javaClass.getDeclaredField("previewContract").apply { isAccessible = true }.get(a))
                call(a, "applyDashboardFilter")
            }
            onView(withId(R.id.openAnalysis)).perform(click())
            awaitActivity(ViopAnalysisActivity::class.java)
            onView(withId(R.id.analysisEmptyTitle)).check(matches(withText("VİOP bağlantısı gerekli")))
            onView(withId(R.id.analysisEmptyAction)).perform(click())
            awaitActivity(SettingsActivity::class.java)
            pressBack()
            awaitActivity(ViopAnalysisActivity::class.java)
            pressBack()
            awaitActivity(ViopActivity::class.java)
        }
    }
    @Test fun compactBistQuickActionsHaveReadableTwoLineLabelsAndWorkingTouchTargets() {
        ActivityScenario.launch(BistScanActivity::class.java).use { scene ->
            WindowFocusAwaiter.await(BistScanActivity::class.java)
            scene.onActivity { activity ->
                val actions = listOf(
                    Triple(R.id.quickSectors, "Sektörler", "Sektörler"),
                    Triple(R.id.quickCriteria, "Kriterler", "Kriterler"),
                    Triple(R.id.quickHistory, "Geçmiş", "Geçmiş Taramalar"),
                    Triple(R.id.quickSaved, "Kayıtlılar", "Kayıtlı Aramalar")
                )
                for ((id, label, spokenName) in actions) {
                    val button = activity.findViewById<TextView>(id)
                    assertNotNull("Missing quick access action: $label", button)
                    assertTrue("$label must remain clickable", button.isClickable)
                    assertEquals("$label accessible label", spokenName, button.contentDescription.toString())
                    assertTrue("$label visible label", button.text.toString().contains(label))
                    button.measure(
                        View.MeasureSpec.makeMeasureSpec(button.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(button.height, View.MeasureSpec.EXACTLY)
                    )
                    val layout = button.layout
                    assertNotNull("$label text layout", layout)
                    assertTrue("$label must fit into two lines", layout.lineCount <= 2)
                    assertFalse("$label must not be truncated", layout.getEllipsisCount(layout.lineCount - 1) > 0)
                }
            }
        }
    }

}
