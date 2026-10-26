package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression checks for source-only delayed observation access from the value-data warning. */
class DelayedScanDialogNavigationSourceTest {
    private val value by lazy { File("src/main/java/tr/borsatakip/v5/ui/CheapStockScanActivity.kt").readText() }
    private val delayed by lazy { File("src/main/java/tr/borsatakip/v5/ui/DelayedObservationActivity.kt").readText() }

    @Test fun missingFinancialDataDialogProvidesProminentDelayedScanAction() {
        assertTrue(value.contains("GECİKMELİ VERİYLE TARA"))
        assertTrue(value.contains("val dialog=android.app.AlertDialog.Builder(this)"))
        assertTrue(value.contains(".setView(alternative)"))
        assertTrue(value.contains("delayedButton.setOnClickListener"))
        assertTrue(value.contains("launchDelayedObservation()"))
    }

    @Test fun existingFinancialDataActionsArePreserved() {
        assertTrue(value.contains(".setPositiveButton(\"JSON DOSYASI SEÇ\")"))
        assertTrue(value.contains(".setNeutralButton(\"VERİ BAĞLANTISI\")"))
        assertTrue(value.contains(".setNegativeButton(\"KAPAT\",null)"))
    }

    @Test fun explicitDelayedButtonTapStartsObservationExactlyThroughOptInIntent() {
        assertTrue(value.contains(".putExtra(FullUniverseScanActivity.EXTRA_LOCAL_DELAYED_MODE,true)"))
        assertTrue(value.contains(".putExtra(FullUniverseScanActivity.EXTRA_AUTO_START,true)"))
        assertTrue(delayed.contains("FullUniverseScanActivity::class.java"))
        assertFalse(delayed.contains("GECİKMELİ GÖZLEMİ BAŞLAT"))
    }

    @Test fun neverPresentsDelayedPriceDataAsFundamentalValuationOrLiveTradeSignal() {
        assertTrue(value.contains("Bilanço, ucuzluk puanı, AL/SAT veya işlem sinyali üretilmez."))
        assertTrue(value.contains("Fiyat listesi bilanço değildir."))
        assertFalse(delayed.contains("FundamentalValuationEngine.scan"))
    }
}
