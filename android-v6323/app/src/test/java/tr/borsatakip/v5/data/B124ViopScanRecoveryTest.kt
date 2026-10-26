package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class B124ViopScanRecoveryTest {
    private fun read(path: String) = File(path).readText()

    @Test fun viopScreenCanRunUnderlyingScanWithoutBackend() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt")
        assertTrue(s.contains("runUnderlyingScan("))
        assertTrue(s.contains("ProviderState.PROVIDER_NOT_CONFIGURED -> runUnderlyingScan"))
        assertTrue(s.contains("DAYANAK TARAMASINI BAŞLAT"))
    }

    @Test fun underlyingScanNeverCreatesFakeViopQuote() {
        val s = read("src/main/java/tr/borsatakip/v5/analysis/ViopUnderlyingScanner.kt")
        assertTrue(s.contains("VİOP fiyatı, hacmi veya açık pozisyon üretmez"))
        assertFalse(s.contains("ViopQuote("))
        assertFalse(s.contains("loadViopQuote"))
    }

    @Test fun viopBackgroundScanIsIndependentFromBistEmptyResult() {
        val s = read("src/main/java/tr/borsatakip/v5/worker/OpportunityWorker.kt")
        val viop = s.indexOf("ViopScanner(viopBackend")
        val early = s.indexOf("if (analyzed.isEmpty()) return Result.success()")
        assertTrue(viop >= 0)
        assertTrue(early > viop)
    }

    @Test fun contractsCanShowExplicitUnderlyingReferenceWithoutFakeContractPrice() {
        val a = read("src/main/java/tr/borsatakip/v5/ui/ViopWatchlistAdapter.kt")
        val c = read("src/main/java/tr/borsatakip/v5/ui/ViopContractsActivity.kt")
        assertTrue(a.contains("Dayanak"))
        assertTrue(c.contains("refreshUnderlyingReferences()"))
        assertTrue(c.contains("VİOP kontrat fiyatı yok"))
    }
}
