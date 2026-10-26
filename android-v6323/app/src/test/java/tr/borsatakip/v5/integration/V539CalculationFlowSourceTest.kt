package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V539CalculationFlowSourceTest {
    private fun source(path: String) = File(path).readText()

    @Test fun activeVersionHasAdvancedTo540() {
        assertTrue(tr.borsatakip.v5.BuildConfig.VERSION_CODE >= 542)
        val build = source("build.gradle.kts")
        val scoring = source("src/main/java/tr/borsatakip/v5/analysis/v531/ScoringConfig.kt")
        assertTrue(build.contains("versionCode = ${tr.borsatakip.v5.BuildConfig.VERSION_CODE}"))
        assertTrue(build.contains("versionName = \"${tr.borsatakip.v5.BuildConfig.VERSION_NAME}\""))
        assertTrue(scoring.contains("ENGINE_VERSION = \"V5.4.1\""))
    }

    @Test fun opportunityEngineUsesCanonicalFinalDecisionFlow() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/OpportunityEngine.kt")
        assertTrue(s.contains("V540FinalDecisionFlow.finalize("))
        assertTrue(s.contains("publishedDirection"))
        assertFalse(s.contains("computeLevels(direction"))
    }

    @Test fun bistMtfIsFinalizedBeforeDownstreamPublication() {
        val s = source("src/main/java/tr/borsatakip/v5/scan/BistScanner.kt")
        assertTrue(s.contains("V531ContextEngine.applyMtf("))
        assertTrue(s.contains("V540FinalDecisionFlow.finalize("))
        assertFalse(s.contains("MtfConsensusEngine.rankingAdjustment"))
        assertFalse(s.contains("rankingScore = (opportunity.rankingScore +"))
    }

    @Test fun viopMtfRebuildsRiskPlanAndRanking() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/ViopScanner.kt")
        assertTrue(s.contains("V531ContextEngine.applyMtf("))
        assertTrue(s.contains("riskPlan = finalized.riskPlan"))
        assertTrue(s.contains("rankingScore = finalized.rankingScore"))
        assertFalse(s.contains("MtfConsensusEngine.rankingAdjustment"))
    }

    @Test fun volatilityUsesDeclaredWeightWithoutHiddenHalfMultiplier() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v531/VolatilityEngine.kt")
        assertTrue(s.contains("val strength = direction * efficiency * health"))
        assertFalse(s.contains("* 0.5"))
    }

    @Test fun viopOiRequiresPointInTimeChangeEvidence() {
        val model = source("src/main/java/tr/borsatakip/v5/model/Models.kt")
        val backend = source("src/main/java/tr/borsatakip/v5/data/BackendProvider.kt")
        val viop = source("src/main/java/tr/borsatakip/v5/analysis/ViopScanner.kt")
        assertTrue(model.contains("openInterestChangePct"))
        assertTrue(model.contains("openInterestAsOfTimestamp"))
        assertTrue(backend.contains("openInterestChangePct"))
        assertTrue(backend.contains("openInterestAsOfTimestamp"))
        assertTrue(viop.contains("openInterestChangePct = quote.openInterestChangePct"))
        assertTrue(viop.contains("openInterestAsOfTimestamp = quote.openInterestAsOfTimestamp"))
    }

    @Test fun remoteRiskRewardIsHashBoundAndGated() {
        val client = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        val hash = source("src/main/java/tr/borsatakip/v5/analysis/v533/V533AuditHash.kt")
        assertTrue(client.contains("V540CalculationOutputSchema.SCHEMA"))
        assertTrue(client.contains("V539RiskRewardGate.evaluate"))
        assertTrue(hash.contains("append(\"rr1=\")"))
        assertTrue(hash.contains("append(\"rr2=\")"))
    }
}
