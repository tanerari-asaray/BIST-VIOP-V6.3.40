package tr.borsatakip.v5.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayedChartDisplaySourceTest {
    private fun source(file: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val root = sequenceOf(File(cwd, "src/main"), File(cwd, "app/src/main"))
            .first { it.isDirectory }
        return File(root, file).readText()
    }

    @Test fun detailAllowsDelayedChartWithoutEnablingScanFallback() {
        val activity = source("java/tr/borsatakip/v5/ui/StockDetailActivity.kt")
        assertTrue(activity.contains("ChartHistoryRepository(quoteProvider, delayedDisplaySource)"))
        assertTrue(activity.contains("loadWithSource(x.symbol, timeframe, x.candles)"))
        assertTrue(activity.contains("GECİKMELİ / YEDEK · SADECE TEKNİK İZLEME · AL/SAT YOK"))
        assertTrue(activity.contains("if (chartUsingDelayedData) { chart.signalPrice = null; chart.signalTime = null }"))
        assertTrue(activity.contains("chartUsingDelayedData || x.source.contains(\"Yahoo\", true)"))
        assertTrue(!activity.contains("experimentalFallbackOverride = true"))
    }

    @Test fun quoteAndTechnicalScreensPreserveDelayedOrigin() {
        val activity = source("java/tr/borsatakip/v5/ui/StockDetailActivity.kt")
        val technical = source("java/tr/borsatakip/v5/ui/TechnicalAnalysisActivity.kt")
        assertTrue(activity.contains("delayedDisplaySource.fetchDisplayQuoteOnly"))
        assertTrue(technical.contains("YahooFallbackProvider(this@TechnicalAnalysisActivity)"))
    }
}
