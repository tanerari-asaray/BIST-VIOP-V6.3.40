package tr.borsatakip.v5.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.Button
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import tr.borsatakip.v5.analysis.TechnicalAnalyzer
import tr.borsatakip.v5.model.Opportunity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.R
import tr.borsatakip.v5.analysis.ChartSupportResistance
import tr.borsatakip.v5.analysis.LinearTrendAnalyzer
import tr.borsatakip.v5.model.Candle
import java.io.File

/** Deterministic offline rendering fixtures, never production market observations. */
@RunWith(AndroidJUnit4::class)
class Build670ChartRuntimeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun fixture() = ArrayList((0 until 80).map { i ->
        Candle(1_700_000_000_000L + i * 300_000L, 100.0,
            if (i % 8 == 2) 110.0 else 103.0, if (i % 8 == 5) 90.0 else 97.0, 100.0, 1000.0)
    })
    private fun intent() = Intent(context, FullscreenChartActivity::class.java)
        .putExtra("symbol", "ASELS").putExtra("market", "BIST").putExtra("timeframe", "FIVE_MIN")
        .putExtra("source", "TEST FIXTURE — NOT MARKET DATA").putExtra("candles", fixture())

    private fun assertSd(scenario: ActivityScenario<out android.app.Activity>, active: Boolean, stage: String = "embedded") {
        scenario.onActivity { activity ->
            val chart = activity.findViewById<PriceChartView>(R.id.chart)
            val button = activity.findViewById<Button>(R.id.toggleSupportResistance)
            assertEquals("$stage chart state", active, chart.showSupportResistance)
            assertEquals("$stage selected state", active, button.isSelected)
            assertEquals(activity.getColor(if (active) R.color.green else R.color.chip_bg), button.backgroundTintList!!.defaultColor)
        }
    }

    @Test fun fullscreenButtonTogglesAndRestoresAfterActivityRecreation() {
        ActivityScenario.launch<FullscreenChartActivity>(intent()).use { scenario ->
            assertSd(scenario, true, "fullscreen initial")
            onView(withId(R.id.toggleSupportResistance)).perform(scrollTo(), click())
            assertSd(scenario, false, "fullscreen off")
            scenario.recreate()
            assertSd(scenario, false, "fullscreen recreated")
            onView(withId(R.id.toggleSupportResistance)).perform(scrollTo(), click())
            assertSd(scenario, true, "fullscreen retoggled")
        }
    }

    @Test fun embeddedChartSdReceivesTouchAndUsesGreenActiveTint() {
        val previous = AppSession.selected
        val bars = fixture()
        val item = Opportunity(symbol="ASELS", companyName="TEST FIXTURE", price=100.0, dailyChangePct=0.0,
            score=0, riskScore=100, direction="NEUTRAL", technicalLabel="TEST", volumeLabel="TEST",
            kapLabel="UNKNOWN", liquidityLabel="TEST", support=null, resistance=null, source="TEST FIXTURE",
            dataTimestamp=bars.last().timestamp, candles=bars, technical=TechnicalAnalyzer.analyze(bars))
        try {
            ActivityScenario.launch<StockDetailActivity>(Intent(context, StockDetailActivity::class.java)
                .putExtra("opportunity", item)).use { scenario ->
                assertSd(scenario, true)
                onView(withId(R.id.toggleSupportResistance)).perform(scrollTo(), click())
                assertSd(scenario, false)
                onView(withId(R.id.toggleSupportResistance)).perform(scrollTo(), click())
                assertSd(scenario, true)
            }
        } finally { AppSession.selected = previous }
    }

    @Test fun smallChartPixelsShowGreenSupportRedResistanceAndHideBoth() {
        ActivityScenario.launch<FullscreenChartActivity>(intent()).use { scenario ->
            scenario.onActivity { activity ->
                val chart = PriceChartView(activity).apply {
                    showEma20 = false; showEma50 = false; showEma200 = false; showBollinger = false
                    showVolume = false; showRsi = false; showMacd = false
                    candles = fixture()
                    supportResistance = ChartSupportResistance.fromConfirmed(candles.dropLast(1))
                    trendAnalysis = LinearTrendAnalyzer.analyze(candles.dropLast(1))
                }
                val density = activity.resources.displayMetrics.density
                val width = (320 * density).toInt(); val height = (300 * density).toInt()
                chart.layout(0, 0, width, height)
                fun draw(name: String): Bitmap {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    chart.draw(Canvas(bitmap))
                    val dir = File(context.filesDir, "build670-screens").apply { mkdirs() }
                    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    return bitmap
                }
                fun pixels(bitmap: Bitmap, color: Int): Int {
                    val data = IntArray(width * height)
                    bitmap.getPixels(data, 0, width, 0, 0, width, height)
                    return data.count { it == color }
                }
                val on = draw("sd-on-320dp-fixture")
                assertTrue(pixels(on, Color.rgb(12, 213, 113)) > 100)
                assertTrue(pixels(on, Color.rgb(255, 84, 84)) > 100)
                chart.showSupportResistance = false
                val off = draw("sd-off-320dp-fixture")
                // Only overlay-specific paint colors must disappear. Bullish candle pixels remain green.
                for (color in listOf(Color.rgb(12, 213, 113), Color.rgb(255, 84, 84))) assertEquals(0, pixels(off, color))
                val saved = chart.saveDisplayState()
                chart.showSupportResistance = true
                chart.restoreDisplayState(saved)
                assertFalse(chart.showSupportResistance)
                chart.showSupportResistance = true
                chart.restoreViewportState(saved)
                assertTrue("Delayed viewport restoration must preserve the latest overlay choice", chart.showSupportResistance)
                on.recycle(); off.recycle()
            }
        }
    }
}
