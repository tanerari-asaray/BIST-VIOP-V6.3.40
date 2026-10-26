package tr.borsatakip.v5.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.R
import tr.borsatakip.v5.model.Candle
import java.io.File

/** Exercises the production JSON source, engine and chart navigation; all records are test fixtures. */
@RunWith(AndroidJUnit4::class)
class ChartFinancialRuntimeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun panel(a: FullscreenChartActivity) = views(a.window.decorView).first { it.tag == "chart-financial-panel" }
    private fun metric(a: FullscreenChartActivity, key: String) = views(panel(a)).filterIsInstance<TextView>()
        .first { it.tag == "financial-value-$key" }
    private fun texts(a: FullscreenChartActivity) = views(panel(a)).filterIsInstance<TextView>().joinToString("\n") { it.text }
    private fun click(a: FullscreenChartActivity, label: String) = views(a.window.decorView).filterIsInstance<Button>()
        .first { it.text.toString() == label }.performClick()
    private fun intent(market: String = "BIST") = Intent(context, FullscreenChartActivity::class.java)
        .putExtra("symbol", "TEST").putExtra("market", market).putExtra("timeframe", "ONE_DAY")
        .putExtra("source", "TEST VERİSİ · gerçek piyasa verisi değildir")
        .putExtra("candles", ArrayList((0 until 240).map { i ->
            val close = 10.0 + i * 0.015
            Candle(System.currentTimeMillis() - (241L - i) * 86400000, close - 0.02, close + 0.1, close - 0.1, close, 1000.0)
        }))
    private fun financialJson(): String {
        val now = System.currentTimeMillis()
        val items = JSONArray()
        (listOf("TEST") + (1..5).map { "PEER$it" }).forEach { symbol ->
            val cap = if (symbol == "TEST") 1000.0 else 2000.0
            items.put(JSONObject().put("symbol", symbol).put("companyName", "TEST ŞİRKETİ · GERÇEK PİYASA VERİSİ DEĞİLDİR")
                .put("sector", "Sanayi (TEST)").put("businessType", "NON_FINANCIAL").put("accountingBasis", "TMS29")
                .put("financialCurrency", "TRY").put("financialPeriodEndMs", now - 70L * 86400000)
                .put("disclosurePublishedAtMs", now - 60L * 86400000).put("marketObservedAtMs", now - 3600000)
                .put("marketCapTry", cap).put("priceTry", cap / 100).put("sharesOutstanding", 100.0)
                .put("netIncomeTtmTry", 100.0).put("equityTry", 500.0).put("ebitdaTtmTry", 200.0)
                .put("netDebtTry", 100.0).put("freeCashFlowTtmTry", 90.0)
                .put("disclosureUrl", "https://www.kap.org.tr/tr/Bildirim/123"))
        }
        return JSONObject().put("schemaVersion", 1).put("items", items).toString()
    }
    private fun withImport(body: (File) -> Unit) {
        val file = File(context.filesDir, "bist-financial-import-v1.json")
        val previous = if (file.isFile) file.readBytes() else null
        try { file.writeText(financialJson()); body(file) }
        finally { if (previous == null) file.delete() else file.writeBytes(previous) }
    }
    private fun waitFor(scene: ActivityScenario<FullscreenChartActivity>, predicate: (FullscreenChartActivity) -> Boolean) {
        repeat(60) {
            var done = false; scene.onActivity { done = predicate(it) }
            if (done) return
            SystemClock.sleep(100)
        }
        error("Financial UI condition timed out")
    }
    private fun capture(name: String, scene: ActivityScenario<FullscreenChartActivity>) {
        val inst = InstrumentationRegistry.getInstrumentation()
        scene.onActivity { views(it.window.decorView).forEach(View::invalidate) }
        inst.waitForIdleSync(); SystemClock.sleep(800)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("Screenshot missing")
        val directory = File(context.filesDir, "build660-screens").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val streams = inst.uiAutomation.executeShellCommandRw("sh")
        val command = "mkdir -p /data/local/tmp/build660-screens && run-as ${context.packageName} cat files/build660-screens/$name.png > /data/local/tmp/build660-screens/$name.png && echo EXPORTED\n"
        ParcelFileDescriptor.AutoCloseOutputStream(streams[1]).use { it.write(command.toByteArray()) }
        assertEquals("EXPORTED", ParcelFileDescriptor.AutoCloseInputStream(streams[0]).bufferedReader().use { it.readText().trim() })
    }

    @Test fun chartOpensFinancialCardsWithImportedComparableStatements() = withImport {
        ActivityScenario.launch<FullscreenChartActivity>(intent()).use { scene ->
            scene.onActivity { click(it, "FİNANSAL") }
            waitFor(scene) { metric(it, "F/K").text.toString() != "—" }
            scene.onActivity { a ->
                assertTrue(panel(a).isShown)
                assertEquals("10,00×", metric(a, "F/K").text.toString())
                assertEquals("2,00×", metric(a, "PD/DD").text.toString())
                assertEquals("0,50×", metric(a, "Net Borç/FAVÖK").text.toString())
                val text = texts(a)
                assertTrue(text.contains("KULLANICI JSON")); assertTrue(text.contains("bağımsız doğrulanmadı"))
                assertTrue(text.contains("SEKTÖRÜNE GÖRE KIYASLA")); assertTrue(text.contains("5 karşılaştırılabilir emsal"))
                assertTrue(text.contains("Şirket: 10,00×")); assertTrue(text.contains("Sektör medyanı: 20,00×"))
                assertTrue(text.contains("PEER1, PEER2, PEER3, PEER4, PEER5"))
                assertEquals(240, a.findViewById<PriceChartView>(R.id.chart).candles.size)
            }
            capture("10-chart-financial-fixture", scene)
            scene.onActivity { a ->
                (views(a.window.decorView).first { it.tag == "chart-financial-scroll" } as ScrollView).fullScroll(View.FOCUS_DOWN)
            }
            capture("12-chart-sector-fixture", scene)
        }
    }

    @Test fun failedFinancialRefreshClearsRatiosAndPeersWithoutClearingRecordedChart() = withImport { file ->
        ActivityScenario.launch<FullscreenChartActivity>(intent()).use { scene ->
            scene.onActivity { click(it, "FİNANSAL") }
            waitFor(scene) { metric(it, "F/K").text.toString() != "—" }
            file.writeText("{}")
            scene.onActivity { a ->
                click(a, "YENİLE")
                assertEquals("—", metric(a, "F/K").text.toString())
                assertEquals("—", metric(a, "PD/DD").text.toString())
                assertEquals("—", metric(a, "Net Borç/FAVÖK").text.toString())
                assertFalse(views(panel(a)).any { it.tag == "financial-comparison" })
                assertEquals(240, a.findViewById<PriceChartView>(R.id.chart).candles.size)
            }
            waitFor(scene) { texts(it).contains("FİNANSAL VERİ ALINAMADI") }
            scene.onActivity { a ->
                assertFalse(views(panel(a)).any { it.tag == "financial-comparison" })
                assertTrue(texts(a).contains("Önceki oranlar ve sektör grafikleri temizlendi."))
            }
            capture("11-chart-financial-error-cleared", scene)
        }
    }

    @Test fun viopFinancialTabDoesNotUseEquityStatements() = withImport { file ->
        file.writeText("{}"); ActivityScenario.launch<FullscreenChartActivity>(intent("VIOP")).use { scene ->
            scene.onActivity { a ->
                click(a, "FİNANSAL")
                assertTrue(texts(a).contains("VİOP · ŞİRKET ORANLARI UYGULANMAZ"))
                assertEquals("—", metric(a, "F/K").text.toString())
                assertEquals("—", metric(a, "PD/DD").text.toString())
                assertEquals("—", metric(a, "Net Borç/FAVÖK").text.toString())
                assertFalse(views(panel(a)).any { it.tag == "financial-comparison" })
                assertEquals(240, a.findViewById<PriceChartView>(R.id.chart).candles.size)
            }
        }
    }
}
