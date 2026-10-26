package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class B127MergedIntegrityAndNavigationTest {
    private fun source(path: String) = File(path).readText()

    @Test fun underlyingCardClickInvokesOpen() {
        val s = source("src/main/java/tr/borsatakip/v5/ui/ViopUnderlyingOpportunityAdapter.kt")
        assertTrue(s.contains("private val onOpen"))
        assertTrue(s.contains("holder.detail.setOnClickListener { onOpen(x) }"))
        assertTrue(s.contains("holder.itemView.setOnClickListener { onOpen(x) }"))
        assertFalse(s.contains("holder.itemView.setOnClickListener(null)"))
    }

    @Test fun underlyingResolvesToRealViopContractBeforeDetail() {
        val s = source("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt")
        assertTrue(s.contains("openRealViopContract(candidate)"))
        assertTrue(s.contains("ViopContractSelector.candidates"))
        assertTrue(s.contains("backend.loadViopQuote(contract.symbol).getOrThrow()"))
        assertTrue(s.contains("backend.loadViopHistoryFlexible(contract.symbol, \"1y\", \"1d\", 0)"))
        assertTrue(s.contains("AppSession.selectedViopContract = contract"))
    }

    @Test fun providerErrorDoesNotSilentlyStartUnderlyingScan() {
        val s = source("src/main/java/tr/borsatakip/v5/ui/ViopActivity.kt")
        assertTrue(s.contains("VİOP provider doğrulanamadı"))
        assertFalse(s.contains("else runUnderlyingScan(\"VİOP provider doğrulanamadı"))
    }

    @Test fun correctedV4IntegrityLayersRemainPresent() {
        assertTrue(File("src/main/java/tr/borsatakip/v5/data/ViopHistoryIntegrityPolicy.kt").exists())
        assertTrue(File("src/main/java/tr/borsatakip/v5/data/ScanTelemetry.kt").exists())
        assertTrue(File("src/main/java/tr/borsatakip/v5/scan/ScanOrchestrator.kt").exists())
        val cache = source("src/main/java/tr/borsatakip/v5/data/MtfHistoryCache.kt")
        assertTrue(cache.contains("lastExchangeTimestamp"))
        assertFalse(cache.contains("OhlcvResampler.sanitize(loader())"))
    }
}
