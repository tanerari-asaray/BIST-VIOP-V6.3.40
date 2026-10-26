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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.R
import tr.borsatakip.v5.data.*
import tr.borsatakip.v5.model.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Test data only; actual input events cover independent references, filtering and failure recovery. */
@RunWith(AndroidJUnit4::class)
class StocksReferencesRuntimeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var preferences: Map<String, *>
    private lateinit var previous: List<Opportunity>
    private val up = TechnicalSnapshot(10.0, 9.0, 8.0, 55.0, 0.1, 0.08, 11.0, 9.0, 0.3, 10.1, 1.2, 9.0, 11.0)
    private val down = up.copy(ema20 = 11.0, ema50 = 12.0, ema200 = 13.0, rsi14 = 40.0, macd = -0.1, macdSignal = 0.0)
    private fun fixture(symbol: String, technical: TechnicalSnapshot) = Opportunity(symbol = symbol, companyName = null,
        price = 10.25, dailyChangePct = null, score = 0, riskScore = 20, direction = "NONE", technicalLabel = "TEST",
        volumeLabel = "TEST", kapLabel = "TEST", liquidityLabel = "TEST", support = 9.0, resistance = 11.0,
        source = "TEST VERİSİ", dataTimestamp = System.currentTimeMillis() - 900000, technical = technical,
        candles = (0..34).map { Candle(System.currentTimeMillis() - (36L - it) * 300000, 10.0, 11.0, 9.0, 10.0, 1000.0) },
        isRealtime = false, dataMode = DataMode.DELAYED, signalValidity = SignalValidity.WATCH, decisionState = DecisionState.WATCH)

    @Before fun isolate() {
        preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toMap()
        previous = AppSession.lastOpportunities
        SettingsStore(context).apply { baseUrl = ""; experimentalProvidersEnabled = false; yahooFallbackEnabled = false }
        AppSession.lastOpportunities = listOf(fixture("TESTA", up), fixture("TESTB", down), fixture("TESTC", up.copy(ema20 = 10.25, ema50 = 10.25)))
    }
    @After fun restore() {
        inst.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() } }
        inst.waitForIdleSync(); AppSession.lastOpportunities = previous
        val edit = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear()
        preferences.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value); is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
            is Boolean -> edit.putBoolean(key, value); is Float -> edit.putFloat(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }; assertTrue(edit.commit())
    }
    private fun references() : StocksReferences {
        val members = setOf("TESTA") + (0..28).map { "TST$it" }
        val names = (members + setOf("TESTB", "TESTC")).associateWith { "TEST VERİSİ · KAP UNVANI $it" }
        return StocksReferences(KapStockDirectory(names, members, System.currentTimeMillis()),
            listOf("XU100", "XU030", "XUTUM").associateWith { StockIndexQuote(it, 101.25, null,
                System.currentTimeMillis() - 900000, "TEST VERİSİ · gecikmeli") }, "Endeksler: 3/3 · KAP: 30 üye")
    }
    private fun loader(scene: ActivityScenario<StocksActivity>, fn: suspend () -> StocksReferences) {
        scene.onActivity { a -> a.javaClass.getDeclaredField("referenceLoader").apply { isAccessible = true }.set(a, fn) }
    }
    private fun await(scene: ActivityScenario<StocksActivity>, predicate: (StocksActivity) -> Boolean) {
        repeat(100) { var ok = false; scene.onActivity { ok = predicate(it) }; if (ok) { inst.waitForIdleSync(); return }; SystemClock.sleep(50) }
        error("Expected stocks reference state not reached")
    }
    private fun count(scene: ActivityScenario<StocksActivity>, size: Int) = await(scene) {
        it.findViewById<RecyclerView>(R.id.stocksList).adapter?.itemCount == size }
    private fun refresh(scene: ActivityScenario<StocksActivity>) {
        onView(withId(R.id.stocksDataRefresh)).perform(click())
        await(scene) { it.findViewById<View>(R.id.stocksDataRefresh).isEnabled }
    }
    private fun capture(name: String) {
        inst.waitForIdleSync(); SystemClock.sleep(7000)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("No stocks reference screenshot")
        val file = File(context.filesDir, "build660-screens/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
        val streams = inst.uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(streams[1]).use { it.write(("mkdir -p /data/local/tmp/build660-screens && run-as ${context.packageName} cat files/build660-screens/$name.png > /data/local/tmp/build660-screens/$name.png && echo EXPORTED\n").toByteArray()) }
        assertEquals("EXPORTED", ParcelFileDescriptor.AutoCloseInputStream(streams[0]).bufferedReader().use { it.readText().trim() })
    }

    @Test fun independentIndicesAndCompanyNamesLoadWithoutIndexRowsInStockScan() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            count(scene, 3); loader(scene) { references() }; refresh(scene)
            onView(withId(R.id.index100Value)).check(matches(withText(org.hamcrest.Matchers.containsString("101"))))
            onView(withId(R.id.indexAllChange)).check(matches(withText("—")))
            onView(withId(R.id.stocksSearch)).perform(replaceText("UNVANI TESTA")); closeSoftKeyboard(); count(scene, 1)
            onView(withId(R.id.stockCompany)).check(matches(withText("TEST VERİSİ · KAP UNVANI TESTA")))
            onView(withId(R.id.stockCompany)).perform(click())
            scene.onActivity { a -> a.findViewById<TextView>(R.id.stocksMarketLabel).text = "TEST VERİSİ · GERÇEK PİYASA VERİSİ DEĞİLDİR" }
            capture("28-stocks-references-loaded-fixture")
            onView(withId(R.id.index100Value)).perform(click())
            onView(withId(android.R.id.message)).check(matches(withText(org.hamcrest.Matchers.containsString("Canlı fiyat değildir"))))
            capture("31-stocks-source-neon-dialog")
        }
    }

    @Test fun verifiedMembershipReallyFiltersAndDelayedTechnicalDirectionStaysSeparateFromLiveSignals() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            count(scene, 3); loader(scene) { references() }; refresh(scene)
            onView(withId(R.id.stocksFilterBist30)).perform(scrollTo(), click()); count(scene, 1)
            onView(withId(R.id.stockSymbol)).check(matches(withText("TESTA")))
            capture("29-stocks-bist30-filter-fixture")
            onView(withId(R.id.stocksFilterAll)).perform(scrollTo(), click()); count(scene, 3)
            onView(withId(R.id.stocksFilterLong)).perform(scrollTo(), click()); count(scene, 0)
            onView(withId(R.id.stocksFilterTrendUp)).perform(scrollTo(), click()); count(scene, 1)
            onView(withId(R.id.stockSymbol)).check(matches(withText("TESTA")))
            onView(withId(R.id.stocksFilterTrendDown)).perform(scrollTo(), click()); count(scene, 1)
            onView(withId(R.id.stockSymbol)).check(matches(withText("TESTB")))
            onView(withId(R.id.stocksFilterAll)).perform(scrollTo(), click()); count(scene, 3)
        }
    }

    @Test fun failedRefreshClearsOldIndexPricesBlocksDuplicatesAndCanRetry() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            count(scene, 3); loader(scene) { references() }; refresh(scene)
            val gate = CompletableDeferred<Unit>(); val calls = AtomicInteger()
            loader(scene) { calls.incrementAndGet(); gate.await(); error("TEST fetch failure") }
            onView(withId(R.id.stocksDataRefresh)).perform(click())
            await(scene) { !it.findViewById<View>(R.id.stocksDataRefresh).isEnabled }
            onView(withId(R.id.index100Value)).check(matches(withText("—")))
            onView(withId(R.id.stocksDataRefresh)).perform(click()); assertEquals(1, calls.get())
            gate.complete(Unit)
            await(scene) { it.findViewById<View>(R.id.stocksDataRefresh).isEnabled }
            onView(withId(R.id.index30Value)).check(matches(withText("—")))
            capture("30-stocks-reference-error-cleared")
            loader(scene) { references() }; refresh(scene)
            onView(withId(R.id.indexAllValue)).check(matches(withText(org.hamcrest.Matchers.containsString("101"))))
        }
    }

    @Test fun expiredMembershipAndFlatOrMissingCandlesRemainExplicit() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            count(scene, 3); loader(scene) { references().let { it.copy(directory = it.directory!!.copy(fetchedAt = System.currentTimeMillis() - 3600001)) } }; refresh(scene)
            onView(withId(R.id.stocksFilterBist30)).perform(scrollTo(), click())
            onView(withText("BIST 30 filtresi kullanılamıyor")).check(matches(isDisplayed()))
            onView(withId(android.R.id.button1)).perform(click()); count(scene, 3)
            onView(withId(R.id.stocksSearch)).perform(replaceText("TESTA")); closeSoftKeyboard(); count(scene, 1)
            onView(withId(R.id.stockMiniChartState)).check(matches(withText("Sabit seri")))
            onView(withId(R.id.stockChange)).check(matches(withText("—")))
            scene.onActivity { AppSession.lastOpportunities = listOf(AppSession.lastOpportunities.first().copy(candles = emptyList())) }
            onView(withId(R.id.stocksSearch)).perform(replaceText("TESTA")); closeSoftKeyboard()
            await(scene) { it.findViewById<TextView>(R.id.stockMiniChartState)?.text == "Mum yok" }
        }
    }

    @Test fun officialSectorMembershipActuallyChangesTheRows() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            count(scene, 3)
            loader(scene) { references().let { it.copy(directory = it.directory!!.copy(memberships =
                mapOf("XUBNK" to setOf("TESTA"), "XUSIN" to setOf("TESTB"), "XUTEK" to setOf("TESTC")))) } }
            refresh(scene)
            for ((id, symbol) in listOf(R.id.stocksFilterBank to "TESTA",R.id.stocksFilterIndustry to "TESTB",R.id.stocksFilterTech to "TESTC")) {
                onView(withId(id)).perform(scrollTo(), click()); count(scene, 1)
                onView(withId(R.id.stockSymbol)).check(matches(withText(symbol)))
                onView(withId(R.id.stocksFilterAll)).perform(scrollTo(), click()); count(scene, 3)
            }
        }
    }

    @Test fun quoteRefreshUpdatesPriceWithoutRewritingTheDecisionAndRejectsDuplicates() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            count(scene, 3)
            val original=AppSession.lastOpportunities.first()
            val gate=CompletableDeferred<Unit>(); val calls=AtomicInteger()
            val fn: suspend (List<String>, (Int, Int) -> Unit) -> StockQuoteRefreshResult = { _, progress ->
                calls.incrementAndGet(); progress(0,3);gate.await()
                StockQuoteRefreshResult(mapOf("TESTA" to DisplayQuoteSnapshot(110.0,100.0,System.currentTimeMillis(),"TEST VERİSİ")),emptyMap())
            }
            scene.onActivity { a -> a.javaClass.getDeclaredField("stockQuoteLoader").apply { isAccessible=true }.set(a,fn) }
            onView(withId(R.id.stocksQuoteRefresh)).perform(click())
            await(scene) { !it.findViewById<View>(R.id.stocksQuoteRefresh).isEnabled }
            onView(withId(R.id.stocksQuoteRefresh)).perform(click());assertEquals(1,calls.get())
            gate.complete(Unit);await(scene) { it.findViewById<View>(R.id.stocksQuoteRefresh).isEnabled }
            val updated=AppSession.lastOpportunities.first()
            assertEquals(110.0,updated.displayQuote!!.price,0.0)
            assertEquals(original.price,updated.price,0.0);assertEquals(original.technical,updated.technical)
            assertEquals(original.exchangeTimestamp,updated.exchangeTimestamp)
            onView(withId(R.id.stocksSearch)).perform(replaceText("TESTA"));closeSoftKeyboard();count(scene,1)
            onView(withId(R.id.stockChange)).check(matches(withText(org.hamcrest.Matchers.containsString("10"))))
        }
    }

    @Test fun failedQuoteRefreshIsVisibleAndDoesNotPromoteOldPrices() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            count(scene, 3)
            val original=AppSession.lastOpportunities
            val fn: suspend (List<String>, (Int, Int) -> Unit) -> StockQuoteRefreshResult = { _, _ -> error("HTTP 401") }
            scene.onActivity { a -> a.javaClass.getDeclaredField("stockQuoteLoader").apply { isAccessible=true }.set(a,fn) }
            onView(withId(R.id.stocksQuoteRefresh)).perform(click())
            await(scene) { it.findViewById<View>(R.id.stocksQuoteRefresh).isEnabled }
            onView(withId(R.id.stocksStatus)).check(matches(withText(org.hamcrest.Matchers.containsString("AUTH_ERROR"))))
            assertEquals(original,AppSession.lastOpportunities)
        }
    }

    @Test fun disconnectedProviderIsOneActionableDialogWithoutChangingDecisions() {
        ActivityScenario.launch(StocksActivity::class.java).use { scene ->
            count(scene, 3)
            val original = AppSession.lastOpportunities
            val code = "LICENSED_BIST_PROVIDER_NOT_CONNECTED"
            val fn: suspend (List<String>, (Int, Int) -> Unit) -> StockQuoteRefreshResult = { symbols, _ ->
                StockQuoteRefreshResult(emptyMap(), symbols.associateWith { code }, symbols.associateWith { symbol ->
                    BackendHttpDiagnostic.fromResponse(503, "/v1/bist/quote/$symbol", """{"code":"$code"}""")
                })
            }
            scene.onActivity { a -> a.javaClass.getDeclaredField("stockQuoteLoader").apply { isAccessible = true }.set(a, fn) }
            onView(withId(R.id.stocksQuoteRefresh)).perform(click())
            await(scene) { it.findViewById<View>(R.id.stocksQuoteRefresh).isEnabled }
            onView(withId(android.R.id.message)).check(matches(withText(org.hamcrest.Matchers.containsString("BIST veri kaynağı sunucuda bağlı değil"))))
            onView(withId(android.R.id.message)).check(matches(withText(org.hamcrest.Matchers.containsString("3 hisse etkileniyor"))))
            assertEquals(original, AppSession.lastOpportunities)
        }
    }

    @Test fun missingKeyIsReportedBeforeAnyQuoteRequest() {
        SettingsStore(context).apply { baseUrl = "https://data.example"; clearApiKey() }
        var done = -1
        val result = kotlinx.coroutines.runBlocking {
            StocksReferenceRepository(context).refreshStockQuotes(listOf("TESTA", "TESTA", "TESTB")) { completed, total ->
                done = completed; assertEquals(2, total)
            }
        }
        assertEquals(2, done); assertTrue(result.quotes.isEmpty()); assertTrue(result.httpFailures.isEmpty())
        assertEquals(mapOf("TESTA" to "API_KEY_MISSING", "TESTB" to "API_KEY_MISSING"), result.issues)
    }

    @Test fun opportunityCardsKeepQuotePriceTimeAndMissingDailyChangeTogether() {
        inst.runOnMainSync {
            val parent=android.widget.FrameLayout(context)
            val time=System.currentTimeMillis()-10000L
            val original=fixture("TEST",up)
            val quote=DisplayQuoteSnapshot(110.0,100.0,time,"TEST QUOTE")
            val item=original.copy(displayQuote=quote)
            val resultAdapter=ScanResultsAdapter(listOf(item)) {}
            val result=resultAdapter.onCreateViewHolder(parent,0)
            resultAdapter.onBindViewHolder(result,0)
            assertTrue(result.priceMovement.text.contains("110"));assertTrue(result.priceMovement.text.contains("10"))
            val formatted=java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(time))
            assertTrue(result.timeInfo.text.contains(formatted))
            val missing=item.copy(displayQuote=quote.copy(previousDailyClose=null))
            val scan=ScanResultsAdapter(listOf(missing)) {};scan.onBindViewHolder(result,0)
            assertTrue(result.priceMovement.text.contains("110"));assertTrue(result.priceMovement.text.contains("Günlük değişim —"))
            val opportunities=OpportunityAdapter(listOf(missing),emptySet(),{}, {})
            val card=opportunities.onCreateViewHolder(parent,0);opportunities.onBindViewHolder(card,0)
            assertTrue(card.priceMovement.text.contains("110"));assertTrue(card.priceMovement.text.contains("Günlük değişim —"))
            assertEquals(original.price,item.price,0.0);assertEquals(original.technical,item.technical)
        }
    }

    private fun html(count: Int, duplicate: Boolean = false): String {
        val rows = JSONArray()
        repeat(count) { i -> rows.put(JSONObject().put("stockCode", if (duplicate) "TEST0" else "TEST$i").put("title", "TEST VERİSİ · UNVAN $i")) }
        val payload = JSONObject().put("code", "XUTUM").put("content", rows).toString() +
            JSONObject().put("code", "XU030").put("content", rows).toString() + JSONObject().put("code", "XU030").put("content", JSONArray()).toString()
        return "<script>self.__next_f.push(" + JSONArray().put(1).put(payload).toString() + ")</script>"
    }
    @Test fun kapStructuredHydrationYieldsThirtyUniqueMembersAndVerifiedNames() {
        val directory = KapStockDirectoryParser.parse(html(30))
        assertEquals(30, directory.bist30.size); assertEquals("TEST VERİSİ · UNVAN 1", directory.names["TEST1"])
        assertTrue(directory.usable())
    }
    @Test fun kapPartialDuplicateAndMalformedResponsesAreRejected() {
        assertTrue(runCatching { KapStockDirectoryParser.parse(html(29)) }.isFailure)
        assertTrue(runCatching { KapStockDirectoryParser.parse(html(30, true)) }.isFailure)
        assertTrue(runCatching { KapStockDirectoryParser.parse("<html>No data</html>") }.isFailure)
    }
}
