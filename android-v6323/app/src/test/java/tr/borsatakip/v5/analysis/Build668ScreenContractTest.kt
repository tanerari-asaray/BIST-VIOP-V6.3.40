package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Build668ScreenContractTest {
    private val code by lazy {
        File("src/main/java/tr/borsatakip/v5/ui/FullscreenChartActivity.kt").readText()
    }

    @Test fun tradingViewActionComesBeforeThePriceNoticeAndTabs() {
        val button = code.indexOf("TRADINGVIEW GRAFİĞİNİ AÇ")
        val notice = code.indexOf("notice = label")
        val tabs = code.indexOf("val tabs = row()")
        assertTrue(button > 0 && notice > button && tabs > notice)
    }

    @Test fun timeframesHaveSeparateActualRequests() {
        assertTrue(code.contains("ChartTimeframe.TEN_MIN"))
        assertTrue(code.contains("comparisonPeriods.forEach { tf ->"))
        assertTrue(code.contains("fetch(tf)"))
        assertTrue(code.contains("Veri yok · Yorum yapılmadı"))
    }

    @Test fun comparisonCardsAreRefreshedOnEveryChartLoadAndWarnOnOldData() {
        val loader = code.substringAfter("private fun load(tf: ChartTimeframe)")
            .substringBefore("private suspend fun fetch")
        assertTrue(loader.contains("refreshTrendComparisons()"))
        assertTrue(code.contains("Eski veri · Yön teyitsiz"))
        assertTrue(code.contains("Mum: ${'$'}{date.format(Date(value.sampleTime))}"))
    }

    @Test fun simpleExplanationIsActuallyAppendedToTheChartAndDetailPages() {
        assertTrue(code.contains("chartPage.addView(readableBottom)"))
        assertTrue(code.contains("renderEverydayExplanation(details, state.candles)"))
        assertTrue(code.contains("BU HİSSEYİ KOLAYCA ANLAYALIM"))
        assertTrue(code.contains("En yeni mum, kapanışı doğrulanmadığı için dışarıda bırakıldı."))
        assertFalse(code.contains("2,65 TL"))
    }
}
