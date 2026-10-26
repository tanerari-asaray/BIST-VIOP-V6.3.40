package tr.borsatakip.v5.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.R
import tr.borsatakip.v5.model.Candle

/** Explicit offline test fixtures; these images do not claim real market data. */
@RunWith(AndroidJUnit4::class)
class ProfessionalChartRuntimeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun button(a: FullscreenChartActivity, text: String) = views(a.window.decorView)
        .filterIsInstance<Button>().first { it.text.toString() == text }
    private fun fixture() = ArrayList((0 until 240).map { i ->
        val close = 10.0 + i * 0.015 + kotlin.math.sin(i * 0.4) * 0.35
        Candle(System.currentTimeMillis() - (241L - i) * 86_400_000, close + if (i % 3 == 0) 0.08 else -0.06,
            close + 0.12, close - 0.15, close, 1000.0 + (i % 7) * 150)
    })
    private fun intent(symbol: String) = Intent(context, FullscreenChartActivity::class.java)
        .putExtra("symbol", symbol).putExtra("market", "BIST").putExtra("timeframe", "ONE_DAY")
        .putExtra("source", "TEST VERİSİ • gerçek piyasa verisi değildir")
        .putExtra("candles", fixture())
    private fun waitFor(scene: ActivityScenario<FullscreenChartActivity>, predicate: (FullscreenChartActivity) -> Boolean) {
        repeat(40) {
            var done = false; scene.onActivity { done = predicate(it) }
            if (done) return
            SystemClock.sleep(100)
        }
        error("Chart UI condition timed out")
    }
    @android.annotation.TargetApi(31)
    private fun capture(name: String, scene: ActivityScenario<FullscreenChartActivity>) {
        val inst = InstrumentationRegistry.getInstrumentation()
        scene.onActivity { views(it.window.decorView).forEach(View::invalidate) }
        inst.waitForIdleSync(); SystemClock.sleep(800)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("Screenshot missing")
        val directory = java.io.File(context.filesDir, "build660-screens").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val streams = inst.uiAutomation.executeShellCommandRw("sh")
        val command = "mkdir -p /data/local/tmp/build660-screens && run-as ${context.packageName} cat files/build660-screens/$name.png > /data/local/tmp/build660-screens/$name.png && echo EXPORTED\n"
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(streams[1]).use { it.write(command.toByteArray()) }
        val result = android.os.ParcelFileDescriptor.AutoCloseInputStream(streams[0]).bufferedReader().use { it.readText().trim() }
        assertEquals("EXPORTED", result)
    }

    @Test fun chartIndicatorsTouchAnalysisAndLandscapeKeepSameFixture() {
        var selectedBeforeRotation: Candle? = null
        ActivityScenario.launch<FullscreenChartActivity>(intent("TEST")).use { scene ->
            waitFor(scene) { it.findViewById<PriceChartView>(R.id.chart).isLaidOut }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            SystemClock.sleep(200)
            scene.onActivity { a ->
                val chart = a.findViewById<PriceChartView>(R.id.chart)
                assertEquals(240, chart.candles.size)
                assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.contains("KAYITLI VERİ") })
                val ema = views(a.window.decorView).filterIsInstance<Button>().first { it.text.startsWith("EMA9") }
                assertTrue(chart.showEma9); ema.performClick(); assertFalse(chart.showEma9)
                ema.performClick(); assertTrue(chart.showEma9)
                val original = chart.onCandleSelected
                var inspected: Candle? = null
                chart.onCandleSelected = { inspected = it; original?.invoke(it) }
                val now = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN to 0.4f, MotionEvent.ACTION_MOVE to 0.7f,
                    MotionEvent.ACTION_UP to 0.7f).forEach { (action, x) ->
                    MotionEvent.obtain(now, now + 50, action, chart.width * x, chart.height * 0.3f, 0).also {
                        chart.dispatchTouchEvent(it); it.recycle()
                    }
                }
                assertNotNull("Dragging must expose a real candle", inspected)
                assertTrue(inspected in chart.candles)
            }
            capture("06-chart-portrait-fixture", scene)
            scene.onActivity { a ->
                val chart = a.findViewById<PriceChartView>(R.id.chart)
                val type = chart.javaClass
                val start = type.getDeclaredField("lastVisibleStart").apply { isAccessible = true }.getInt(chart)
                val count = type.getDeclaredField("lastVisibleCount").apply { isAccessible = true }.getInt(chart)
                val selected = type.getDeclaredField("selectedAbsoluteIndex").apply { isAccessible = true }.get(chart) as Int
                assertTrue("Dragged OHLC must belong to a visible candle", selected in start until (start + count))
                selectedBeforeRotation = chart.selectedCandle
                button(a, "ÖZET").performClick()
                val text = views(a.window.decorView).filterIsInstance<TextView>().joinToString { it.text }
                assertTrue(text.contains("İŞLEM SİNYALİ: YOK")); assertTrue(text.contains("Önceki 20 mum ortalaması"))
                button(a, "AYRINTI").performClick()
                assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "SMA200" })
                button(a, "ÖZET").performClick()
            }
            capture("07-chart-summary-fixture", scene)
            scene.onActivity { a ->
                button(a, "GRAFİK").performClick()
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            waitFor(scene) {
                val chart = it.findViewById<PriceChartView>(R.id.chart)
                it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && chart.width > chart.height
            }
            scene.onActivity { a ->
                val chart = a.findViewById<PriceChartView>(R.id.chart)
                assertEquals(240, chart.candles.size); assertFalse(chart.showRsi)
                assertEquals("Rotation must preserve the inspected candle", selectedBeforeRotation, chart.selectedCandle)
                val selected = requireNotNull(chart.selectedCandle)
                val expectedDate = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale("tr", "TR"))
                    .apply { timeZone = java.util.TimeZone.getTimeZone("Europe/Istanbul") }.format(java.util.Date(selected.timestamp))
                assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.startsWith(expectedDate) && it.text.contains(" · A ") })
                assertTrue(chart.width > chart.height)
                assertTrue("Landscape chart must have room to inspect candles", chart.height >= 160 * a.resources.displayMetrics.density)
            }
            capture("08-chart-landscape-fixture", scene)
        }
    }

    @Test fun panningHistoricalPricesCannotLookLikeTheLastRecordedCandle() {
        ActivityScenario.launch<FullscreenChartActivity>(intent("TEST")).use { scene ->
            waitFor(scene) { it.findViewById<PriceChartView>(R.id.chart).contentDescription.contains("SON KAYIT") }
            var latestTimestamp = 0L
            scene.onActivity { a ->
                val chart = a.findViewById<PriceChartView>(R.id.chart)
                latestTimestamp = chart.candles.last().timestamp
                assertTrue(chart.contentDescription.contains("Canlı fiyat değildir"))
                val now = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN to 0.3f, MotionEvent.ACTION_MOVE to 0.75f,
                    MotionEvent.ACTION_UP to 0.75f).forEachIndexed { i, (action, x) ->
                    MotionEvent.obtain(now, now + i * 80L, action, chart.width * x, chart.height * 0.3f, 0).also {
                        chart.dispatchTouchEvent(it); it.recycle()
                    }
                }
            }
            waitFor(scene) { it.findViewById<PriceChartView>(R.id.chart).contentDescription.contains("GEÇMİŞ") }
            scene.onActivity { a ->
                val chart = a.findViewById<PriceChartView>(R.id.chart)
                val type = chart.javaClass
                val start = type.getDeclaredField("lastVisibleStart").apply { isAccessible = true }.getInt(chart)
                val count = type.getDeclaredField("lastVisibleCount").apply { isAccessible = true }.getInt(chart)
                val visibleLast = chart.candles[start + count - 1]
                assertTrue("The gesture must actually move into historical candles", visibleLast.timestamp < latestTimestamp)
                val date = java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale("tr", "TR"))
                    .apply { timeZone = java.util.TimeZone.getTimeZone("Europe/Istanbul") }
                val description = chart.contentDescription.toString()
                assertTrue(description.contains("GEÇMİŞ ${date.format(java.util.Date(visibleLast.timestamp))}"))
                assertTrue(description.contains("K ${"%.2f".format(visibleLast.close)}"))
                assertFalse(description.contains("SON KAYIT"))
                assertTrue(description.contains("Canlı fiyat değildir"))
                chart.resetZoom()
            }
            waitFor(scene) { it.findViewById<PriceChartView>(R.id.chart).contentDescription.contains("SON KAYIT") }
            scene.onActivity { a ->
                val chart = a.findViewById<PriceChartView>(R.id.chart)
                val latest = chart.candles.last()
                assertEquals(latestTimestamp, latest.timestamp)
                assertFalse(chart.contentDescription.contains("GEÇMİŞ"))
                assertTrue(chart.contentDescription.contains("K ${"%.2f".format(latest.close)}"))
                assertTrue(chart.contentDescription.contains("Canlı fiyat değildir"))
            }
        }
    }

    @Test fun failedNewPeriodClearsRecordedChartAndAllAnalysisImmediately() {
        // Missing symbol deterministically fails before contacting any external provider.
        ActivityScenario.launch<FullscreenChartActivity>(intent("")).use { scene ->
            scene.onActivity { a ->
                assertEquals(240, a.findViewById<PriceChartView>(R.id.chart).candles.size)
                button(a, "5 DK").performClick()
                assertTrue(a.findViewById<PriceChartView>(R.id.chart).candles.isEmpty())
            }
            waitFor(scene) { a -> views(a.window.decorView).filterIsInstance<TextView>().any { it.text.contains("Veri alınamadı.") } }
            scene.onActivity { a ->
                val chart = a.findViewById<PriceChartView>(R.id.chart)
                assertTrue(chart.candles.isEmpty()); assertNull(chart.signalPrice)
                assertTrue(chart.contentDescription.contains("temizlendi"))
                val text = views(a.window.decorView).filterIsInstance<TextView>().joinToString { it.text }
                assertTrue(text.contains("Fiyat / OHLCV: —")); assertTrue(text.contains("ANALİZ YOK"))
                assertFalse(text.contains("SON MUM KAPANIŞI"))
                button(a, "YENİLE").performClick()
                assertTrue(chart.candles.isEmpty())
            }
            waitFor(scene) { a -> views(a.window.decorView).filterIsInstance<TextView>().any { it.text.contains("Veri alınamadı.") } }
            capture("09-chart-error-cleared", scene)
        }
    }

    @Test fun viopDetailRejectsOldChartWhenRequestedPeriodIsUnavailable() {
        val previousContract = AppSession.selectedViopContract
        val previousOpportunity = AppSession.selectedViopOpportunity
        try {
            AppSession.selectedViopOpportunity = null
            AppSession.selectedViopContract = tr.borsatakip.v5.model.ViopContract("F_TEST1226", "TEST", "2026-12", isManual = true)
            ActivityScenario.launch<ViopDetailActivity>(Intent(context, ViopDetailActivity::class.java)).use { scene ->
                scene.onActivity { a ->
                    val type = a.javaClass
                    type.getDeclaredField("candles").apply { isAccessible = true }.set(a, fixture())
                    type.getDeclaredMethod("renderChart").apply { isAccessible = true }.invoke(a)
                    val chart = a.findViewById<PriceChartView>(R.id.viopPriceChart)
                    assertFalse(chart.candles.isEmpty())
                    type.getDeclaredMethod("loadTimeframe", ViopDetailTimeframe::class.java)
                        .apply { isAccessible = true }.invoke(a, ViopDetailTimeframe.ONE_HOUR)
                    assertTrue(chart.candles.isEmpty()); assertNull(chart.signalPrice)
                    assertNull(type.getDeclaredField("technical").apply { isAccessible = true }.get(a))
                }
            }
        } finally {
            AppSession.selectedViopContract = previousContract
            AppSession.selectedViopOpportunity = previousOpportunity
        }
    }
    @Test fun viopReevaluationRestoresTheCanonicalResultAndChartClearingDoesNotDowngradeIt() {
        val selected=AppSession.selectedViopContract
        val previous=AppSession.selectedViopOpportunity
        val contract=tr.borsatakip.v5.model.ViopContract("F_TEST1226", "TEST", "2026-12", isManual=true)
        val technical=tr.borsatakip.v5.analysis.TechnicalAnalyzer.analyze(fixture())
        val quote=tr.borsatakip.v5.model.ViopQuote(contract.symbol,100.0,null,null,null,null,null,
            System.currentTimeMillis(),System.currentTimeMillis(),"TEST VERİSİ",false,null,false)
        val canonical=tr.borsatakip.v5.model.ViopOpportunity(contract,quote,fixture(),technical,
            90,20,90,0,90,10,90,"LONG","TEST CANONICAL RESULT",tr.borsatakip.v5.model.SignalValidity.VALID,0L,240,
            decisionState=tr.borsatakip.v5.model.DecisionState.VERIFIED_OPPORTUNITY)
        val gate=kotlinx.coroutines.CompletableDeferred<Unit>()
        try {
            AppSession.selectedViopContract=contract;AppSession.selectedViopOpportunity=canonical
            ActivityScenario.launch<ViopDetailActivity>(Intent(context,ViopDetailActivity::class.java)).use { scene ->
                scene.onActivity { a ->
                    val loader:suspend(String)->Result<tr.borsatakip.v5.model.ViopOpportunity> = { symbol ->
                        assertEquals(contract.symbol,symbol);gate.await();Result.success(canonical)
                    }
                    a.javaClass.getDeclaredField("signalLoader").apply { isAccessible=true }.set(a,loader)
                    a.javaClass.getDeclaredMethod("reanalyzeSignal").apply { isAccessible=true }.invoke(a)
                    val pending=a.javaClass.getDeclaredField("opportunity").apply { isAccessible=true }.get(a) as tr.borsatakip.v5.model.ViopOpportunity
                    assertEquals(tr.borsatakip.v5.model.SignalValidity.WATCH,pending.validity)
                }
                gate.complete(Unit)
                repeat(100) {
                    var ready=false
                    scene.onActivity { a -> ready=a.javaClass.getDeclaredField("opportunity").apply { isAccessible=true }.get(a)==canonical }
                    if(!ready) SystemClock.sleep(50)
                }
                scene.onActivity { a ->
                    val field=a.javaClass.getDeclaredField("opportunity").apply { isAccessible=true }
                    assertEquals(canonical,field.get(a))
                    a.javaClass.getDeclaredMethod("clearChartData").apply { isAccessible=true }.invoke(a)
                    assertEquals("Chart refresh must not mutate the separate decision",canonical,field.get(a))
                    val failed:suspend(String)->Result<tr.borsatakip.v5.model.ViopOpportunity> = { throw IllegalStateException("TEST HTTP 401") }
                    val loader=a.javaClass.getDeclaredField("signalLoader").apply { isAccessible=true }
                    val reanalyze=a.javaClass.getDeclaredMethod("reanalyzeSignal").apply { isAccessible=true }
                    loader.set(a,failed);reanalyze.invoke(a)
                    val failure=field.get(a) as tr.borsatakip.v5.model.ViopOpportunity
                    assertEquals(tr.borsatakip.v5.model.SignalValidity.WATCH,failure.validity)
                    assertTrue(failure.signalReason.contains("TEST HTTP 401"))
                    val recovered:suspend(String)->Result<tr.borsatakip.v5.model.ViopOpportunity> = { Result.success(canonical) }
                    loader.set(a,recovered);reanalyze.invoke(a)
                    assertEquals(canonical,field.get(a))
                }
            }
        } finally { AppSession.selectedViopContract=selected;AppSession.selectedViopOpportunity=previous }
    }

    @Test fun chartViewportAndIndicatorStateRestoreAfterSeriesRebind() {
        ActivityScenario.launch<FullscreenChartActivity>(intent("TEST")).use { scene ->
            waitFor(scene) { it.findViewById<PriceChartView>(R.id.chart).isLaidOut }
            scene.onActivity { activity ->
                val chart=activity.findViewById<PriceChartView>(R.id.chart)
                chart.showEma9=false;chart.showSma200=false;chart.showAtr=true
                chart.showTrendLines=false;chart.showChannel=false;chart.zoomIn()
                val saved=chart.saveDisplayState()
                val candles=chart.candles
                chart.candles=candles;chart.showEma9=true;chart.showSma200=true
                chart.showAtr=false;chart.showTrendLines=true;chart.showChannel=true
                chart.restoreDisplayState(saved)
                assertFalse(chart.showEma9);assertFalse(chart.showSma200)
                assertTrue(chart.showAtr);assertFalse(chart.showTrendLines);assertFalse(chart.showChannel)
                assertEquals(saved.getFloat("zoom"),chart.saveDisplayState().getFloat("zoom"),0.0001f)
            }
        }
    }

}
