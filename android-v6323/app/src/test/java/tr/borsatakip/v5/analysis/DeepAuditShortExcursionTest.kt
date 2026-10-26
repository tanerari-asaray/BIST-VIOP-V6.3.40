package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.borsatakip.v5.analysis.v540.backtest.V540BacktestEngine
import tr.borsatakip.v5.analysis.v540.backtest.V540BacktestSignal
import tr.borsatakip.v5.analysis.v540.backtest.V540ExecutionCostConfig
import tr.borsatakip.v5.analysis.v540.backtest.V540ExecutionPolicy
import tr.borsatakip.v5.analysis.v540.backtest.V540EntryExecutionMode
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.RiskPlan

/** Conventional entry-notional percentage excursions, including a short decline of 100 -> 80. */
class DeepAuditShortExcursionTest {
    private val t0 = 1_700_000_000_000L
    private val plan = RiskPlan(100.0, 110.0, 70.0, 60.0, 3.0, 4.0)
    private val bars = listOf(Candle(t0 + 60_000L, 100.0, 105.0, 80.0, 90.0, 1000.0))

    @Test fun forwardShortMfeUsesEntryNotional() {
        val result = TradeForwardEngine.evaluate("SHORT", t0, plan, bars)
        assertEquals(20.0, requireNotNull(result.mfePct), 1e-9)
    }

    @Test fun backtestShortMfeUsesEntryNotional() {
        val signal = V540BacktestSignal(
            signalId = "audit-short", symbol = "THYAO", instrumentType = "BIST",
            timeframeMinutes = 1, signalTimestamp = t0, direction = "SHORT",
            signalScore = 80, confidence = 85, riskScore = 30, rankingScore = 75,
            entryPrice = 100.0, riskPlan = plan, futureCandles = bars
        )
        val result = V540BacktestEngine.evaluate(signal, V540ExecutionCostConfig())
        assertEquals(20.0, requireNotNull(result.mfePct), 1e-9)
    }
    @Test fun intrabarShortMfeUsesActualEntryNotional() {
        val parent = Candle(t0 + 300_000L, 100.0, 105.0, 80.0, 90.0, 1000.0)
        val signal = V540BacktestSignal(
            signalId = "audit-short-intrabar", symbol = "THYAO", instrumentType = "BIST",
            timeframeMinutes = 5, signalTimestamp = t0, direction = "SHORT",
            signalScore = 80, confidence = 85, riskScore = 30, rankingScore = 75,
            entryPrice = 100.0, riskPlan = plan, futureCandles = listOf(parent),
            intrabarCandles = listOf(parent))
        val result = V540BacktestEngine.evaluate(signal, V540ExecutionCostConfig(),
            V540ExecutionPolicy(entryMode = V540EntryExecutionMode.LIMIT_AT_ENTRY))
        assertEquals(20.0, requireNotNull(result.mfePct), 1e-9)
        assertEquals(-5.0, requireNotNull(result.maePct), 1e-9)
    }

    @Test fun longExcursionAndShortAdverseExcursionKeepEntryDenominator() {
        val longPlan = RiskPlan(100.0, 90.0, 130.0, 140.0, 3.0, 4.0)
        val longBars = listOf(Candle(t0 + 60_000L, 100.0, 120.0, 95.0, 110.0, 1000.0))
        val long = TradeForwardEngine.evaluate("LONG", t0, longPlan, longBars)
        val short = TradeForwardEngine.evaluate("SHORT", t0, plan, bars)
        assertEquals(20.0, requireNotNull(long.mfePct), 1e-9)
        assertEquals(-5.0, requireNotNull(short.maePct), 1e-9)
    }
}
