package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.model.Candle
import java.io.File

class ChartSupportResistanceOverlayTest {
    private fun bar(i: Int, close: Double = 100.0, high: Double = 106.0, low: Double = 94.0) =
        Candle(1_786_100_000_000L + i * 300_000L, close, high, low, close, 1000.0)

    @Test fun noVerifiedHistoryMeansNoSupportOrResistance() {
        val levels = ChartSupportResistance.fromConfirmed((0 until 6).map { bar(it) })
        assertNull(levels.support)
        assertNull(levels.resistance)
    }

    @Test fun horizontalLevelsExistEvenWhenRegressionCannotGiveSlopedSupport() {
        val candles = (0 until 50).map { i ->
            bar(i, 100.0, if (i % 8 == 2) 110.0 else 103.0,
                if (i % 8 == 5) 90.0 else 97.0)
        }
        val levels = ChartSupportResistance.fromConfirmed(candles)
        assertNotNull(levels.support)
        assertNotNull(levels.resistance)
        assertTrue(levels.support!!.price < 100.0)
        assertTrue(levels.resistance!!.price > 100.0)
        assertEquals(ChartSupportResistance.Evidence.CONFIRMED_PIVOT, levels.support!!.evidence)
        assertEquals(ChartSupportResistance.Evidence.CONFIRMED_PIVOT, levels.resistance!!.evidence)
    }

    @Test fun monotonicHistoryDoesNotManufactureFutureResistance() {
        val candles = (0 until 60).map { i ->
            val price = 70.0 + i
            bar(i, price, price + 0.4, price - 0.4)
        }
        val levels = ChartSupportResistance.fromConfirmed(candles)
        assertNotNull(levels.support)
        assertNull(levels.resistance)
        assertEquals(ChartSupportResistance.Evidence.RECENT_RANGE, levels.support!!.evidence)
    }

    @Test fun supportResistanceRenderingUsedByBothChartActivities() {
        val path = "src/main/java/tr/borsatakip/v5/ui/"
        val view = File(path + "PriceChartView.kt").readText()
        val stock = File(path + "StockDetailActivity.kt").readText()
        val fullscreen = File(path + "FullscreenChartActivity.kt").readText()
        assertTrue(view.contains("drawVerifiedLevels(canvas"))
        assertTrue(view.contains("var showSupportResistance: Boolean = true"))
        assertTrue(view.contains("if (showSupportResistance) drawVerifiedLevels("))
        assertTrue(view.contains("supportBandPaint"))
        assertTrue(view.contains("resistanceBandPaint"))
        assertTrue(stock.contains("ChartSupportResistance.fromConfirmed(candles.dropLast(1))"))
        assertTrue(fullscreen.contains("ChartSupportResistance.fromConfirmed(state.candles.dropLast(1))"))
    }
}
