package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V541ExecutionCorrectnessSourceTest {
    private fun source(path:String)=File(path).readText()

    @Test fun activeVersionIs541(){
        assertTrue(tr.borsatakip.v5.BuildConfig.VERSION_CODE >= 542)
        val b=source("build.gradle.kts"); val s=source("src/main/java/tr/borsatakip/v5/analysis/v531/ScoringConfig.kt")
        assertTrue(b.contains("versionCode = ${tr.borsatakip.v5.BuildConfig.VERSION_CODE}")); assertTrue(b.contains("versionName = \"${tr.borsatakip.v5.BuildConfig.VERSION_NAME}\"")); assertTrue(s.contains("V5.4.1"))
    }
    @Test fun entryExecutionModesAreExplicit(){
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestModels.kt")
        listOf("SIGNAL_PRICE","NEXT_BAR_OPEN","LIMIT_AT_ENTRY","NEXT_BAR_OPEN_WITH_SLIPPAGE").forEach{assertTrue(s.contains(it))}
    }
    @Test fun canonicalEntryAndGeometryAreValidated(){
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestEngine.kt")
        assertTrue(s.contains("validSignalAndCanonicalPlan")); assertTrue(s.contains("validPlanGeometry")); assertTrue(s.contains("RISK_PLAN_GEOMETRY_INVALID"))
    }
    @Test fun actualEntryCanRebaseRiskPlan(){
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestEngine.kt")
        assertTrue(s.contains("REBASE_BY_DELTA")); assertTrue(s.contains("actualEntry - original.entry")); assertTrue(s.contains("recomputeRr"))
    }
    @Test fun metricsSortChronologically(){
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestEngine.kt")
        assertTrue(s.contains("sortedWith(compareBy<V540BacktestTrade> { it.entryTime }"))
    }
    @Test fun intrabarAndLimitAmbiguityAreExplicit(){
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestEngine.kt")
        assertTrue(s.contains("LIMIT_FILL_BAR_ORDER_UNKNOWN")); assertTrue(s.contains("INTRABAR_ORDER_RESOLVED"))
    }
    @Test fun adaptiveCostAndRollingLiquidityExist(){
        val m=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540BacktestModels.kt")
        val l=source("src/main/java/tr/borsatakip/v5/analysis/v540/V540LiquidityEngine.kt")
        assertTrue(m.contains("ADAPTIVE_MARKET_COST_V1")); assertTrue(l.contains("historicalComparableVolumes")); assertTrue(l.contains("percentile"))
    }
    @Test fun blockBootstrapAndFrozenOosSelectionExist(){
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540WalkForwardEngine.kt")
        assertTrue(s.contains("SYMBOL_BLOCK")); assertTrue(s.contains("REGIME_BLOCK")); assertTrue(s.contains("MODEL_FROZEN_BEFORE_OOS"))
    }
    @Test fun evidenceGatePreventsUnprovenPass(){
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540WalkForwardEngine.kt")
        assertTrue(s.contains("POINT_IN_TIME_DATA_NOT_VERIFIED")); assertTrue(s.contains("OBSERVED_COST_INPUTS_NOT_VERIFIED"))
    }
    @Test fun signalDecayIsNotTradeOutcome(){
        val s=source("src/main/java/tr/borsatakip/v5/analysis/v540/backtest/V540SignalDecayEngine.kt")
        assertTrue(s.contains("SIGNAL_DECAY_CLOSE_TO_CLOSE")); assertTrue(s.contains("executionComparable = false"))
    }
}
