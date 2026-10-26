package tr.borsatakip.v5.ui

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.model.Candle

/** Compiled for emulator/physical-device execution; the fixture is NOT market data. */
@RunWith(AndroidJUnit4::class)
class Build668ChartRuntimeTest {
    private fun children(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun tradingViewButtonAndPlainLanguagePanelsExistInOfflineFixture() {
        val data = ArrayList((0 until 80).map { i ->
            val p = 12.0 + i * 0.02
            Candle(1_700_000_000_000L + i * 300_000L, p, p + 0.2, p - 0.2, p, 1000.0)
        })
        val intent = Intent(context, FullscreenChartActivity::class.java).apply {
            putExtra("symbol", "DEMO")
            putExtra("market", "BIST")
            putExtra("source", "YALNIZCA TEST VERİSİ")
            putExtra("timeframe", "FIVE_MIN")
            putExtra("candles", data)
        }
        ActivityScenario.launch<FullscreenChartActivity>(intent).use { scenario ->
            scenario.onActivity { a ->
                val components = children(a.window.decorView)
                assertTrue(components.filterIsInstance<Button>().any { it.text.contains("TRADINGVIEW GRAFİĞİNİ AÇ") })
                assertTrue(components.filterIsInstance<TextView>().any { it.text.contains("BU HİSSEYİ KOLAYCA ANLAYALIM") })
                assertTrue(components.filterIsInstance<TextView>().any { it.text.contains("Fiyat nasıl hareket ediyor?") })
                assertTrue(components.filterIsInstance<TextView>().any { it.text.contains("KAYITLI VERİ") })
            }
        }
    }
}
