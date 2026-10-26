package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Guards real Android 360dp/1.3-font layout fixes and display-only publication safety. */
class Build668SmallScreenUiRegressionTest {
    private val technicalLayout by lazy { File("src/main/res/layout/activity_technical_analysis.xml").readText() }
    private val technicalActivity by lazy { File("src/main/java/tr/borsatakip/v5/ui/TechnicalAnalysisActivity.kt").readText() }
    private val nav by lazy { File("src/main/res/layout/include_bottom_nav.xml").readText() }
    private val stockActivity by lazy { File("src/main/java/tr/borsatakip/v5/ui/StockDetailActivity.kt").readText() }
    private val scan by lazy { File("src/main/res/layout/activity_scan_results.xml").readText() }

    @Test fun technicalTabsDoNotWrapWordsInTheMiddle() {
        assertTrue(technicalLayout.contains("Teknik analiz bölümleri; diğer sekmeler için yatay kaydırın"))
        assertTrue(technicalLayout.contains("android:singleLine=\"true\""))
        assertTrue(technicalLayout.contains("android:scrollbars=\"none\""))
    }

    @Test fun technicalHeadlineCannotPublishUnverifiedTradeDirection() {
        assertTrue(technicalActivity.contains("OpportunityPublicationPolicy.enforceVerifiedPublication(x)"))
        assertTrue(technicalActivity.contains("publication.decisionState == DecisionState.VERIFIED_OPPORTUNITY"))
        assertTrue(technicalActivity.contains("TEKNİK İZLEME • AL/SAT ONAYI YOK"))
    }

    @Test fun navUsesVectorsAndReadableLabelsInsteadOfInlineGlyphs() {
        assertEquals(5, Regex("android:drawableTop=\"@drawable/ic_sd_nav_").findAll(nav).count())
        assertTrue(nav.contains("android:text=\"Ana Sayfa\""))
        assertTrue(nav.contains("android:text=\"Hisseler\""))
    }

    @Test fun sourceTimestampDoesNotRepeatDelayBadgeOrClipProvider() {
        assertTrue(stockActivity.contains("source.substringBefore(\"•\").substringBefore(\" / \")"))
        assertTrue(stockActivity.contains("Son veri"))
    }

    @Test fun scoreFilterShortLabelNeverClipsAt360dp() {
        assertTrue(scan.contains("android:id=\"@+id/scan85\""))
        assertTrue(scan.contains("android:text=\"85+\""))
    }
}
