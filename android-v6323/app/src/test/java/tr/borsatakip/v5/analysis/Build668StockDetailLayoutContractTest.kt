package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Checks the design acceptance contract independently of downloaded quote data. */
class Build668StockDetailLayoutContractTest {
    private val layout by lazy { File("src/main/res/layout/activity_stock_detail.xml").readText() }
    private val activity by lazy { File("src/main/java/tr/borsatakip/v5/ui/StockDetailActivity.kt").readText() }

    @Test fun tradingViewStaysAboveTabsAndChart() {
        val tradingView = layout.indexOf("@+id/btnTradingViewChart")
        val cards = layout.indexOf("@+id/multiTimeframeSummary")
        val tabs = layout.indexOf("@+id/detailTabChart")
        val chart = layout.indexOf("@+id/chart\"")
        assertTrue(tradingView > 0 && tradingView < cards && cards < tabs && tabs < chart)
    }

    @Test fun fourPeriodsHaveRealIndependentRequestsAndClickableChartControls() {
        assertTrue(listOf("ChartTimeframe.FIVE_MIN", "ChartTimeframe.TEN_MIN", "ChartTimeframe.ONE_HOUR", "ChartTimeframe.ONE_DAY").all { activity.contains(it) })
        assertTrue(listOf("quick5m", "quick10m", "quick1h", "quick1d", "trend5m", "trend15m", "trend1h", "trend1d").all { layout.contains("@+id/$it") })
        assertTrue(activity.contains("chartRepository.loadWithSource(x.symbol, timeframe, x.candles)"))
    }

    @Test fun technicalExplanationAndDataFreshnessCannotUseSamplePrice() {
        assertTrue(layout.contains("BU HİSSEYİ KOLAYCA ANLAYALIM"))
        assertTrue(layout.contains("@+id/technicalConflict"))
        assertTrue(layout.contains("@+id/quoteSource"))
        assertTrue(layout.contains("@+id/graphSupport") && layout.contains("@+id/resistanceDetail"))
        assertFalse(layout.contains("2,65"))
        assertTrue(activity.contains("clearSelectedAnalysis()"))
    }
}
