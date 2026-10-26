package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V540CalculationBacktestSourceTest {
    private fun source(path:String)=File(path).readText()

    @Test fun activeVersionIs540() {
        assertTrue(tr.borsatakip.v5.BuildConfig.VERSION_CODE >= 542)
        val build=source("build.gradle.kts"); val scoring=source("src/main/java/tr/borsatakip/v5/analysis/v531/ScoringConfig.kt")
        assertTrue(build.contains("versionCode = ${tr.borsatakip.v5.BuildConfig.VERSION_CODE}")); assertTrue(build.contains("versionName = \"${tr.borsatakip.v5.BuildConfig.VERSION_NAME}\"")); assertTrue(scoring.contains("V5.4.1"))
    }

    @Test fun structuralTargetsHaveNoQuarterAtrFloorBeyondBarrier() {
        val s=source("src/main/java/tr/borsatakip/v5/analysis/V540FinalDecisionFlow.kt")
        assertFalse(s.contains("price + 0.25 * atr")); assertFalse(s.contains("price - 0.25 * atr"))
        assertTrue(s.contains("barrier?.let { min(modelT2, it) }")); assertTrue(s.contains("barrier?.let { max(modelT2, it) }"))
        assertTrue(s.contains("val target1 = min(requiredT1, target2)")); assertTrue(s.contains("val target1 = max(requiredT1, target2)"))
        assertTrue(s.contains("target1 > it || target2 > it")); assertTrue(s.contains("target1 < it || target2 < it"))
    }

    @Test fun baseCalculationRejectsMtfInjection() {
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v531/V531CalculationEngine.kt")
        assertTrue(s.contains("MTF_CONTEXT_MUST_BE_APPLIED_ONLY_BY_V531ContextEngine"))
        assertFalse(s.contains("MultiTimeframeEngine.evaluate(input.mtfConsensusScore)"))
    }

    @Test fun freshnessUsesContinuousPolicy() {
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/V540FreshnessPolicy.kt")
        assertTrue(s.contains("lerp")); assertFalse(s.contains("5_001L"))
    }

    @Test fun rankingHasExplicitStatusAndRrIsGateOnly() {
        val model=source("src/main/java/tr/borsatakip/v5/model/Models.kt")
        val rank=source("src/main/java/tr/borsatakip/v5/analysis/v540/V540RankingEngine.kt")
        assertTrue(model.contains("enum class RankingStatus")); assertTrue(rank.contains("RR_USED_AS_GATE_ONLY"))
    }

    @Test fun backtestHasCostsMfeMaeWalkForwardAndOos() {
        val b=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestEngine.kt")
        val w=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540WalkForwardEngine.kt")
        val m=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestModels.kt")
        assertTrue(b.contains("mfePct")); assertTrue(b.contains("maePct")); assertTrue(b.contains("totalCostPct")); assertTrue(w.contains("V540WalkForwardWindow") && w.contains("testEnd")); assertTrue(m.contains("val outOfSample"))
    }
}
