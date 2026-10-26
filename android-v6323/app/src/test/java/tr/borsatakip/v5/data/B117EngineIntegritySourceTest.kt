package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class B117EngineIntegritySourceTest {
    private fun source(path: String): String = File(path).readText()

    @Test fun scanResultsUsesCanonicalRankingAndDoesNotDoubleCountFailures() {
        val s = source("src/main/java/tr/borsatakip/v5/ui/ScanResultsActivity.kt")
        assertTrue(s.contains("OpportunityRankingPolicy.sort(filteredItems)"))
        assertTrue(s.contains("scanState.skipped.coerceAtLeast(scanState.failedSymbols.size)"))
        // Canonical non-double-count expression must be used in both summary/detail paths.
        assertTrue(s.contains("val failed = scanState.skipped.coerceAtLeast(scanState.failedSymbols.size)"))
    }

    @Test fun versionCodeTracksCurrentIntegrityBuild() {
        val s = File("build.gradle.kts").readText()
        assertTrue(Regex("versionCode\\s*=\\s*(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { it >= 135 } == true)
    }
}
