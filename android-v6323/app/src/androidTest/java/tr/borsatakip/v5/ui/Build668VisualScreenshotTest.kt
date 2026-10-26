package tr.borsatakip.v5.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.widget.ScrollView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.model.Candle
import java.io.File

/** Strict Android screenshots: offline TEST fixtures, never real market data. */
@RunWith(AndroidJUnit4::class)
class Build668VisualScreenshotTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun all(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    private fun fixture(): ArrayList<Candle> = ArrayList((0 until 90).map { i ->
        val price = 12.0 + i * 0.022
        Candle(1_790_000_000_000L + i * 300_000L, price, price + 0.12, price - 0.10, price + 0.02,
            1000.0 + i * 4)
    })

    private fun start(data: ArrayList<Candle>): Intent =
        Intent(context, FullscreenChartActivity::class.java).apply {
            putExtra("symbol", "TEST")
            putExtra("market", "BIST")
            putExtra("source", "TEST FIKSTURU - GERCEK FIYAT DEGIL")
            putExtra("timeframe", "FIVE_MIN")
            putExtra("candles", data)
        }

    private fun image(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screen capture failed" }
        val folder = File(context.filesDir, "build668-screens").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        val (read, write) = instrumentation.uiAutomation.executeShellCommandRw("sh")
        val script = "mkdir -p /data/local/tmp/build668-screens && " +
            "run-as ${context.packageName} cat files/build668-screens/$name.png " +
            "> /data/local/tmp/build668-screens/$name.png && echo EXPORTED\n"
        ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(script.toByteArray()) }
        val result = ParcelFileDescriptor.AutoCloseInputStream(read).bufferedReader().use { it.readText() }
        assertTrue("Screenshot export failed: $result", result.contains("EXPORTED"))
    }

    @Test fun topAndEasyExplanationScreenshotsUseActualAndroidViews() {
        ActivityScenario.launch<FullscreenChartActivity>(start(fixture())).use { scenario ->
            scenario.onActivity { a ->
                val labels = all(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(labels.any { it.contains("BU HİSSEYİ KOLAYCA ANLAYALIM") })
                assertTrue(labels.any { it.contains("Fiyat nasıl hareket ediyor?") })
                assertTrue(labels.any { it.contains("Kısaca ne iş yapıyor?") })
                assertTrue(labels.any { it.contains("Neden ilgi görüyor?") })
                assertTrue(labels.any { it.contains("Dikkat edilmesi gerekenler") })
                assertTrue(labels.any { it.contains("KAYITLI VERİ") })
            }
            image("01-grafik-ust")
            scenario.onActivity { a ->
                val scroll = all(a.window.decorView).filterIsInstance<ScrollView>().first()
                scroll.post { scroll.scrollTo(0, scroll.getChildAt(0).height) }
            }
            image("02-kolay-aciklama-alt")
        }
    }

    @Test fun invalidCandleCannotGenerateFalseMarketAnalysis() {
        // Nonempty invalid input forces the real chart state into an error, without HTTP requests.
        val bad = arrayListOf(Candle(1_790_000_000_000L, 12.0, 12.2, 11.8, 12.0, -5.0))
        ActivityScenario.launch<FullscreenChartActivity>(start(bad)).use { scenario ->
            scenario.onActivity { a ->
                val labels = all(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(labels.any { it.contains("Veri olmadan yorum yapmıyoruz") })
                assertFalse(labels.any { it.contains("Yükseliş eğilimi · Kapanış") })
            }
            image("03-veri-yok")
        }
    }
}
