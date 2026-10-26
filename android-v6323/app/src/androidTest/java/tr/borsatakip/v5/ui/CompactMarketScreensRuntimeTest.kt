package tr.borsatakip.v5.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.ViopUnderlyingScanner
import tr.borsatakip.v5.analysis.OpportunityTrendPresentation
import tr.borsatakip.v5.data.SettingsStore
import tr.borsatakip.v5.data.favorites.FavoriteRepository
import tr.borsatakip.v5.model.*
import java.io.File

/** Offline view fixtures only. No screenshot or assertion proves a live BIST/VIOP connection. */
@RunWith(AndroidJUnit4::class)
class CompactMarketScreensRuntimeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var previous: List<Opportunity>
    private lateinit var previousSettings: Map<String, *>
    private val technical = TechnicalSnapshot(10.1, 10.0, 9.5, 55.0, 0.1, 0.08, 11.0, 9.0, 0.3, 10.1, 1.2, 9.0, 11.0)
    @Before fun isolateFixtures() {
        previous = AppSession.lastOpportunities
        previousSettings = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toMap()
        // TESTING prevents all startup network work. Individual UI fixtures then use NOT_CONFIGURED.
        SettingsStore(context).apply {
            baseUrl = "https://fixture.invalid"
            apiKey = "offline-ui-test-key"
            lastProviderState = "PROVIDER_TESTING"
            experimentalProvidersEnabled = false
            yahooFallbackEnabled = false
        }
    }
    @After fun restoreState() {
        AppSession.lastOpportunities = previous
        val edit = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear()
        previousSettings.forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue(edit.commit())
    }
    private fun fixture(symbol: String, direction: String = "NONE", live: Boolean = false) = Opportunity(
        symbol = symbol, companyName = "Test Şirketi · TEST VERİSİ", price = 10.25, dailyChangePct = -1.2,
        score = 80, riskScore = 20, direction = direction, technicalLabel = "TEST", volumeLabel = "TEST",
        kapLabel = "TEST", liquidityLabel = "TEST", support = 9.0, resistance = 11.0, source = "TEST VERİSİ",
        dataTimestamp = System.currentTimeMillis() - if (live) 1000 else 900000,
        candles = (0 until 35).map { i -> Candle(System.currentTimeMillis() - (36L-i)*300000,
            10.0+i*0.01, 10.2+i*0.01, 9.8+i*0.01, 10.1+i*0.01, 1000.0+i) }, technical = technical,
        isRealtime = live, dataMode = if (live) DataMode.REALTIME else DataMode.DELAYED,
        signalValidity = if (live) SignalValidity.VALID else SignalValidity.WATCH,
        decisionState = if (live) DecisionState.VERIFIED_OPPORTUNITY else DecisionState.WATCH)
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun set(activity: Activity, field: String, value: Any) {
        activity.javaClass.getDeclaredField(field).apply { isAccessible = true }.set(activity, value)
    }
    private fun call(activity: Activity, method: String) {
        activity.javaClass.getDeclaredMethod(method).apply { isAccessible = true }.invoke(activity)
    }
    private fun <A: Activity> waitFor(scene: ActivityScenario<A>, predicate: (A) -> Boolean) {
        repeat(60) {
            var done = false; scene.onActivity { done = predicate(it) }
            if (done) { inst.waitForIdleSync(); return }
            SystemClock.sleep(100)
        }
        error("Market UI condition timed out")
    }
    private fun <A: Activity> capture(name: String, scene: ActivityScenario<A>) {
        var layerType = View.LAYER_TYPE_NONE
        scene.onActivity { a ->
            // Full software composition avoids dropped unchanged GPU tiles in headless API 35 captures.
            layerType = a.window.decorView.layerType
            a.window.decorView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            views(a.window.decorView).forEach { it.requestLayout(); it.invalidate() }
            for (id in listOf(R.id.navHome, R.id.navBist, R.id.navViop, R.id.navFav, R.id.navSettings)) {
                assertTrue(a.findViewById<View>(id).isShown)
                assertTrue(a.findViewById<TextView>(id).text.isNotEmpty())
            }
        }
        inst.waitForIdleSync(); SystemClock.sleep(1200)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("Screenshot missing")
        val directory = File(context.filesDir, "build660-screens").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        scene.onActivity { it.window.decorView.setLayerType(layerType, null) }
        val streams = inst.uiAutomation.executeShellCommandRw("sh")
        val command = "mkdir -p /data/local/tmp/build660-screens && run-as ${context.packageName} cat files/build660-screens/$name.png > /data/local/tmp/build660-screens/$name.png && echo EXPORTED\n"
        ParcelFileDescriptor.AutoCloseOutputStream(streams[1]).use { it.write(command.toByteArray()) }
        assertEquals("EXPORTED", ParcelFileDescriptor.AutoCloseInputStream(streams[0]).bufferedReader().use { it.readText().trim() })
    }
    private fun stocks() = listOf(fixture("TESTA", "LONG"), fixture("TESTB", "SHORT"),
        fixture("TESTC", "LONG", true), fixture("TESTD"), fixture("TESTE"))

    @Test fun stocksSearchVerifiedFiltersFavoritesAndEmptyRecovery() {
        AppSession.lastOpportunities = stocks()
        val repo = FavoriteRepository.get(context)
        val wasFavorite = runBlocking { repo.isFavorite("TESTA") }
        try {
            ActivityScenario.launch(StocksActivity::class.java).use { scene ->
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 5 }
                scene.onActivity { a ->
                    a.findViewById<TextView>(R.id.stocksMarketLabel).text = "TEST VERİSİ · GERÇEK PİYASA VERİSİ DEĞİLDİR"
                    assertEquals("—", a.findViewById<TextView>(R.id.index100Value).text.toString())
                    assertTrue(a.findViewById<TextView>(R.id.stocksIndexNotice).text.contains("3 endeks"))
                    a.findViewById<Button>(R.id.stocksFilterBist30).performClick()
                    assertTrue(a.findViewById<TextView>(R.id.stocksStatus).text.contains("doğrulanmadı"))
                    assertEquals(5, a.findViewById<RecyclerView>(R.id.stocksList).adapter!!.itemCount)
                }
                onView(withId(android.R.id.button1)).perform(click())
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).findViewHolderForAdapterPosition(0) != null }
                scene.onActivity { a ->
                    val row = a.findViewById<RecyclerView>(R.id.stocksList).findViewHolderForAdapterPosition(0)!!.itemView
                    row.findViewById<TextView>(R.id.stockFavorite).performClick()
                }
                repeat(40) { if (runBlocking { repo.isFavorite("TESTA") } != wasFavorite) return@repeat; SystemClock.sleep(50) }
                assertEquals(!wasFavorite, runBlocking { repo.isFavorite("TESTA") })
                scene.onActivity { it.findViewById<Button>(R.id.stocksFilterAll).performClick() }
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 5 }
                capture("13-stocks-compact-fixture", scene)
                val detailMonitor = inst.addMonitor(StockDetailActivity::class.java.name, null, true)
                try {
                    scene.onActivity { a ->
                        val row = a.findViewById<RecyclerView>(R.id.stocksList).findViewHolderForAdapterPosition(0)!!.itemView
                        assertTrue(row.performClick())
                    }
                    assertEquals(1, detailMonitor.hits)
                } finally { inst.removeMonitor(detailMonitor) }
                scene.onActivity { it.findViewById<Button>(R.id.stocksFilterLong).performClick() }
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 1 }
                scene.onActivity { it.findViewById<Button>(R.id.stocksFilterShort).performClick() }
                waitFor(scene) { it.findViewById<View>(R.id.stocksEmptyState).visibility == View.VISIBLE }
                scene.onActivity { assertEquals(0, it.findViewById<RecyclerView>(R.id.stocksList).adapter!!.itemCount) }
                capture("14-stocks-filter-empty", scene)
                scene.onActivity { it.findViewById<Button>(R.id.stocksEmptyAction).performClick() }
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 5 }
                scene.onActivity { it.findViewById<EditText>(R.id.stocksSearch).setText("TESTB") }
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 1 }
                scene.onActivity { it.findViewById<EditText>(R.id.stocksSearch).setText("MISSING") }
                waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 0 }
            }
        } finally {
            runBlocking { if (wasFavorite) repo.add("TESTA", "Test Şirketi") else repo.remove("TESTA") }
        }
    }

    @Test fun stocksNoDataActionAndNavigationKeepTheirDestinations() {
        AppSession.lastOpportunities = emptyList()
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            waitFor(scene) { it.findViewById<View>(R.id.stocksEmptyState).visibility == View.VISIBLE }
            for ((id, target) in listOf(R.id.stocksEmptyAction to BistScanActivity::class.java,
                R.id.stocksScanCard to BistScanActivity::class.java, R.id.stocksFavorites to FavoritesActivity::class.java,
                R.id.stocksSettings to SettingsActivity::class.java, R.id.stocksNotifications to NotificationsActivity::class.java)) {
                val monitor = inst.addMonitor(target.name, null, true)
                try {
                    scene.onActivity { assertTrue(it.findViewById<View>(id).performClick()) }
                    assertEquals(target.name, 1, monitor.hits)
                } finally { inst.removeMonitor(monitor) }
            }
            capture("15-stocks-no-data", scene)
        }
    }

    @Test fun stocksRotationKeepsQueryAndFilterAndCollapsesHeaderForMoreRows() {
        AppSession.lastOpportunities = stocks()
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 5 }
            scene.onActivity { a ->
                a.findViewById<Button>(R.id.stocksFilterLong).performClick()
                a.findViewById<EditText>(R.id.stocksSearch).setText("TESTC")
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            waitFor(scene) { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 1 }
            scene.onActivity { a ->
                assertTrue(a.findViewById<Button>(R.id.stocksFilterLong).isSelected)
                assertEquals("TESTC", a.findViewById<EditText>(R.id.stocksSearch).text.toString())
                a.findViewById<AppBarLayout>(R.id.marketAppBar).setExpanded(false, false)
            }
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).findViewHolderForAdapterPosition(0) != null }
            capture("16-stocks-landscape-fixture", scene)
            scene.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    @Test fun stocksLiveSignalExpiresWhileScreenRemainsOpen() {
        AppSession.lastOpportunities = listOf(fixture("TESTX", "LONG", true)
            .copy(exchangeTimestamp = System.currentTimeMillis() - 45_000L))
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 1 }
            scene.onActivity { it.findViewById<Button>(R.id.stocksFilterLong).performClick() }
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 1 }
            var expired = false
            for (attempt in 0 until 200) {
                scene.onActivity { expired = it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == 0 }
                if (expired) break
                SystemClock.sleep(150)
            }
            assertTrue("Expired quote must leave the live LONG filter without a manual refresh", expired)
            scene.onActivity { assertTrue(it.findViewById<View>(R.id.stocksEmptyState).isShown) }
        }
    }

    @Test fun compactStockTrendColoursAndStrengthResetWhenHolderIsReused() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            scene.onActivity { activity ->
                val strong = fixture("BRIGHT", "NONE")
                val normal = strong.copy(symbol = "NORMAL", technical = technical.copy(ema200 = null))
                val down = strong.copy(symbol = "DOWN", price = 9.0, dailyChangePct = 4.0,
                    technical = technical.copy(ema20 = 9.5, ema50 = 10.0, ema200 = 11.0,
                        rsi14 = 40.0, macd = -2.0, macdSignal = -1.0))
                val missing = strong.copy(symbol = "MISSING", technical = technical.copy(ema20 = null))
                val rows = listOf(strong, normal, down, missing)
                val adapter = StocksCompactAdapter(rows, emptySet(), {}, {})
                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                var previousBackground: android.graphics.drawable.Drawable? = null
                rows.forEachIndexed { index, row ->
                    adapter.onBindViewHolder(holder, index)
                    val trend = OpportunityTrendPresentation.from(row).trend
                    assertEquals(trend.color, holder.direction.currentTextColor)
                    assertTrue(holder.direction.text.contains(trend.label))
                    assertTrue(holder.direction.text.contains("GECİKMELİ"))
                    assertNotSame(previousBackground, holder.itemView.background)
                    previousBackground = holder.itemView.background
                }
                assertEquals(activity.getColor(R.color.market_ink), holder.symbol.currentTextColor)
                assertFalse(holder.direction.text.contains("LONG"))
                assertFalse(holder.direction.text.contains("SHORT"))
            }
        }
    }

    @Test fun underlyingTrendHighlightNeverBecomesAContractOrTradeSignal() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            scene.onActivity { activity ->
                val strong = fixture("BRIGHT", "NONE")
                val missing = strong.copy(technical = technical.copy(ema20 = null))
                val rows = listOf(strong, missing).map { row -> ViopUnderlyingScanner.Candidate(
                    ViopContract("F_BRIGHT1299", "BRIGHT", "2099-12"), row) }
                val adapter = ViopUnderlyingOpportunityAdapter(rows) {}
                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                rows.forEachIndexed { index, row ->
                    adapter.onBindViewHolder(holder, index)
                    val trend = OpportunityTrendPresentation.from(row.underlying).trend
                    assertEquals(trend.color, holder.direction.currentTextColor)
                    assertTrue(holder.direction.text.contains("DAYANAK"))
                    assertTrue(holder.direction.text.contains(trend.label))
                    assertEquals("VİOP —", holder.price.text.toString())
                    assertEquals("Sözleşme verisi yok", holder.levels.text.toString())
                    assertFalse(holder.direction.text.contains("LONG"))
                    assertFalse(holder.direction.text.contains("SHORT"))
                    assertTrue(holder.itemView.contentDescription.contains("Gerçek VİOP kontratı değildir"))
                }
                assertEquals(activity.getColor(R.color.market_ink), holder.symbol.currentTextColor)
            }
        }
    }

    private fun injectUnderlying(a: ViopActivity) {
        a.lifecycleScope.coroutineContext.cancelChildren()
        set(a, "providerAutoTestInFlight", true)
        set(a, "underlyingScanCompleted", true)
        SettingsStore(context).baseUrl = ""
        val rows = stocks().filterNot { it.isRealtime }.map { o -> ViopUnderlyingScanner.Candidate(
            ViopContract("F_${o.symbol}1299", o.symbol, "2099-12"), o) }
        set(a, "underlyingItems", rows)
        set(a, "showingUnderlying", true)
        call(a, "applyDashboardFilter"); call(a, "refreshProviderState")
        a.findViewById<TextView>(R.id.providerStatus).text = "TEST VERİSİ · GERÇEK PİYASA VERİSİ DEĞİLDİR · VİOP bağlı değil"
    }
    @Test fun viopUnderlyingCannotImpersonateContractAndFiltersRecover() {
        ActivityScenario.launch(ViopActivity::class.java).use { scene ->
            scene.onActivity { injectUnderlying(it) }
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.list).findViewHolderForAdapterPosition(0) != null }
            scene.onActivity { a ->
                assertEquals(4, a.findViewById<RecyclerView>(R.id.list).adapter!!.itemCount)
                assertEquals("LONG\n—", a.findViewById<TextView>(R.id.summaryLong).text.toString())
                assertEquals("SHORT\n—", a.findViewById<TextView>(R.id.summaryShort).text.toString())
                assertEquals("Vade —", a.findViewById<TextView>(R.id.heroExpiry).text.toString())
                assertTrue(a.findViewById<TextView>(R.id.metricVolume).text.endsWith("—"))
                assertTrue(a.findViewById<TextView>(R.id.metricOi).text.endsWith("—"))
                val row = a.findViewById<RecyclerView>(R.id.list).findViewHolderForAdapterPosition(0)!!.itemView
                assertEquals("VİOP —", row.findViewById<TextView>(R.id.viopPrice).text.toString())
                assertFalse(views(row).filterIsInstance<TextView>().any { it.isShown && it.text.contains("2099-12") })
                assertTrue(row.contentDescription.contains("Gerçek VİOP kontratı değildir"))
            }
            capture("17-viop-compact-underlying-fixture", scene)
            val underlyingMonitor = inst.addMonitor(StockDetailActivity::class.java.name, null, true)
            try {
                scene.onActivity { a ->
                    a.findViewById<RecyclerView>(R.id.list).findViewHolderForAdapterPosition(0)!!.itemView.performClick()
                    assertEquals("TESTA", AppSession.selected?.symbol)
                }
                assertEquals(1, underlyingMonitor.hits)
            } finally { inst.removeMonitor(underlyingMonitor) }
            for ((id, target) in listOf(R.id.openContracts to ViopContractsActivity::class.java,
                R.id.openSettings to SettingsActivity::class.java, R.id.openFavorites to FavoritesActivity::class.java,
                R.id.openNotifications to NotificationsActivity::class.java)) {
                val monitor = inst.addMonitor(target.name, null, true)
                try {
                    scene.onActivity { assertTrue(it.findViewById<View>(id).performClick()) }
                    assertEquals(target.name, 1, monitor.hits)
                } finally { inst.removeMonitor(monitor) }
            }
            val analysisMonitor = inst.addMonitor(ViopAnalysisActivity::class.java.name, null, true)
            try {
                scene.onActivity { it.findViewById<Button>(R.id.openAnalysis).performClick() }
                assertEquals(1, analysisMonitor.hits)
            } finally { inst.removeMonitor(analysisMonitor) }
            scene.onActivity { it.findViewById<Button>(R.id.filterIndex).performClick() }
            scene.onActivity { a ->
                assertEquals(0, a.findViewById<RecyclerView>(R.id.list).adapter!!.itemCount)
                assertTrue(a.findViewById<View>(R.id.viopEmptyState).isShown)
            }
            capture("18-viop-filter-empty", scene)
            scene.onActivity { it.findViewById<Button>(R.id.viopEmptyAction).performClick() }
            scene.onActivity { a ->
                assertEquals(4, a.findViewById<RecyclerView>(R.id.list).adapter!!.itemCount)
                a.findViewById<EditText>(R.id.viopSearch).setText("TESTB")
                assertEquals(1, a.findViewById<RecyclerView>(R.id.list).adapter!!.itemCount)
                a.findViewById<Button>(R.id.filterLong).performClick()
                assertEquals(0, a.findViewById<RecyclerView>(R.id.list).adapter!!.itemCount)
                SettingsStore(context).apply {
                    baseUrl = "https://fixture.invalid"
                    apiKey = "offline-ui-test-key"
                    lastProviderState = "PROVIDER_TESTING"
            experimentalProvidersEnabled = false
            yahooFallbackEnabled = false
                }
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            waitFor(scene) { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            scene.onActivity { a ->
                injectUnderlying(a)
                assertTrue(a.findViewById<Button>(R.id.filterLong).isSelected)
                assertEquals("TESTB", a.findViewById<EditText>(R.id.viopSearch).text.toString())
                assertEquals(0, a.findViewById<RecyclerView>(R.id.list).adapter!!.itemCount)
                a.findViewById<Button>(R.id.viopEmptyAction).performClick()
                a.findViewById<AppBarLayout>(R.id.marketAppBar).setExpanded(false, false)
            }
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.list).findViewHolderForAdapterPosition(0) != null }
            capture("20-viop-landscape-fixture", scene)
            scene.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    @Test fun viopContractFieldsUseOnlyQuoteAndClearWithoutRetainingPreviousMetadata() {
        ActivityScenario.launch(ViopActivity::class.java).use { scene ->
            scene.onActivity { a ->
                injectUnderlying(a)
                val c = ViopContract("F_TEST1226", "TEST", "2026-12", volume = 1234.0, openInterest = 45L)
                val q = ViopQuote(c.symbol, 20.5, 20.4, 20.6, 1.5, 2345.0, 67L,
                    System.currentTimeMillis() - 900000, System.currentTimeMillis(), "TEST VERİSİ", false, 900, true)
                val o = ViopOpportunity(c, q, fixture("TEST").candles, technical, 30, 80, 30, 0,
                    10, 10, 20, "LONG", "TEST", SignalValidity.WATCH, 900000L, 35)
                set(a, "showingUnderlying", false); set(a, "productionItems", listOf(o))
                call(a, "applyDashboardFilter")
                a.javaClass.getDeclaredMethod("renderPreview", ViopContract::class.java, ViopQuote::class.java, List::class.java)
                    .apply { isAccessible = true }.invoke(a, c, q, o.candles)
                assertTrue(a.findViewById<TextView>(R.id.heroExpiry).text.contains("2026-12"))
                assertTrue(a.findViewById<TextView>(R.id.metricOi).text.contains("67"))
                assertFalse(a.findViewById<TextView>(R.id.metricOi).text.contains("45"))
                a.findViewById<TextView>(R.id.providerTitle).text = "TEST · SÖZLEŞME KAYDI"
                a.findViewById<TextView>(R.id.providerStatus).text = "TEST VERİSİ · GERÇEK BAĞLANTI DOĞRULAMASI DEĞİLDİR"
            }
            waitFor(scene) { it.findViewById<RecyclerView>(R.id.list).findViewHolderForAdapterPosition(0) != null }
            scene.onActivity { a ->
                val row = a.findViewById<RecyclerView>(R.id.list).findViewHolderForAdapterPosition(0)!!.itemView
                assertTrue(row.findViewById<TextView>(R.id.viopDirection).text.contains("İZLE"))
                assertTrue(row.findViewById<TextView>(R.id.viopScores).text.contains("GECİKMELİ"))
                assertEquals("20,50", row.findViewById<TextView>(R.id.viopPrice).text.toString().replace('.', ','))
            }
            capture("19-viop-contract-fixture", scene)
            scene.onActivity { a ->
                call(a, "clearPreview")
                assertEquals("Vade —", a.findViewById<TextView>(R.id.heroExpiry).text.toString())
                assertTrue(a.findViewById<TextView>(R.id.metricVolume).text.endsWith("—"))
                assertTrue(a.findViewById<TextView>(R.id.metricOi).text.endsWith("—"))
            }
        }
    }
}
