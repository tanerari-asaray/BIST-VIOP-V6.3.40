package tr.borsatakip.v5.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class B123HomeTabsAndMarketDetailsTest {
    private fun read(path: String) = File(path).readText()

    @Test fun homeContainsLongShortTabs() {
        val xml = read("src/main/res/layout/activity_main.xml")
        assertTrue(xml.contains("@+id/todayLongTab"))
        assertTrue(xml.contains("@+id/todayShortTab"))
    }

    @Test fun homeFiltersExistingResultsByDirection() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/MainActivity.kt")
        assertTrue(s.contains("val verified = all.filter { OpportunityTrendPresentation.from(it).verifiedSignal }"))
        assertTrue(s.contains("TodayMode.LONG -> verified.filter { it.direction.equals(\"LONG\", true) }"))
        assertTrue(s.contains("TodayMode.SHORT -> verified.filter { it.direction.equals(\"SHORT\", true) }"))
    }

    @Test fun everyMarketTileOpensDedicatedDetail() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/MainActivity.kt")
        listOf("marketBist100Card", "marketBist30Card", "marketUsdCard", "marketGoldCard", "marketViopCard")
            .forEach { assertTrue(s.contains(it)) }
        assertTrue(s.contains("MarketInstrumentDetailActivity"))
    }

    @Test fun bist30DetailUsesMultipleValidatedSymbols() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/MarketInstrumentDetailActivity.kt")
        assertTrue(s.contains("listOf(\"XU030.IS\", \"^XU030\", \"XU030\")"))
    }

    @Test fun marketValuesCanShrinkWithoutEllipsis() {
        val xml = read("src/main/res/layout/activity_main.xml")
        assertTrue(xml.contains("android:autoSizeMinTextSize=\"6sp\""))
    }
}
