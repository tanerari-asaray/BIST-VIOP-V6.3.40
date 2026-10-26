package tr.borsatakip.v5.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class B124ViopFallbackUxTest {
    private fun read(path: String) = File(path).readText()

    @Test fun viopUnderlyingScanStartsAutomaticallyWhenBackendIsMissing() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt")
        assertTrue(s.contains("underlyingScanInFlight"))
        assertTrue(s.contains("underlyingScanCompleted"))
        assertTrue(s.contains("runUnderlyingScan(\"Production VİOP backend bağlı değil\")"))
    }

    @Test fun viop30DetailFallsBackOnlyToExplicitUnderlyingReference() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/MarketInstrumentDetailActivity.kt")
        assertTrue(s.contains("loadViop30Underlying"))
        assertTrue(s.contains("BU DEĞER VİOP KONTRAT FİYATI DEĞİLDİR"))
        assertTrue(s.contains("XU030.IS"))
    }

    @Test fun viopTileDoesNotInventContractPriceWhenBackendIsMissing() {
        val s = read("src/main/java/tr/borsatakip/v5/ui/MainActivity.kt")
        assertTrue(s.contains("tile.value.text = \"—\""))
        assertTrue(s.contains("tile.change.text = \"Veri yok\""))
    }
}
