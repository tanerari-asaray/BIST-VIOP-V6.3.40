package tr.borsatakip.v5.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TradingViewChartButtonSourceTest {
    @Test
    fun tradingViewButtonIsTopLevelDynamicAndUsesAndroidRouting() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val mainRoot = sequenceOf(
            File(cwd, "src/main"),
            File(cwd, "app/src/main"),
            cwd.parentFile?.let { File(it, "app/src/main") }
        ).filterNotNull().first { it.isDirectory }

        val layout = File(mainRoot, "res/layout/activity_stock_detail.xml").readText()
        val activity = File(mainRoot, "java/tr/borsatakip/v5/ui/StockDetailActivity.kt").readText()
        val header = layout.indexOf("@+id/title")
        val button = layout.indexOf("@+id/btnTradingViewChart")
        val companyPrice = layout.indexOf("@+id/quoteCompany")
        val liveCard = layout.indexOf("@+id/liveConnectionStatus")

        // The visual reference explicitly places TradingView ABOVE price/subtitle.
        assertTrue("TradingView must be at the top, immediately after the header", header >= 0 && button > header && button < companyPrice && button < liveCard)
        assertTrue("Only one TradingView chart button should exist", Regex("@\\+id/btnTradingViewChart").findAll(layout).count() == 1)
        assertTrue("Link must be built from the active stock symbol", activity.contains("TradingViewChartLink.toBistSymbol(x.symbol)"))
        assertTrue("Android ACTION_VIEW routing must remain so TradingView app can open directly", activity.contains("Intent(Intent.ACTION_VIEW, uri)"))
        assertTrue("KIMMR must not be hardcoded in production activity", !activity.contains("KIMMR"))
    }
}
