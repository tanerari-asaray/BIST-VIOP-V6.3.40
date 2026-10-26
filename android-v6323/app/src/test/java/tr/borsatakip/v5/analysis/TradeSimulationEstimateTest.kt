package tr.borsatakip.v5.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeSimulationEstimateTest {
    @Test
    fun longRoundTripUsesExistingCommissionAndSlippageFormula() {
        val r = TradeForwardEngine.estimateRoundTrip(
            direction = "LONG",
            entry = 100.0,
            exit = 110.0,
            costs = TradeForwardEngine.CostConfig(
                quantity = 10.0,
                contractMultiplier = 1.0,
                commissionPerSide = 2.0,
                slippageBpsPerSide = 10.0
            )
        ).getOrThrow()
        assertEquals(100.0, r.grossPnl, 1e-9)
        assertEquals(6.1, r.totalCosts, 1e-9)
        assertEquals(93.9, r.netPnl, 1e-9)
        assertEquals(9.39, r.netReturnPct, 1e-9)
    }

    @Test
    fun invalidQuantityIsRejectedInsteadOfInventingResult() {
        val r = TradeForwardEngine.estimateRoundTrip(
            direction = "LONG",
            entry = 100.0,
            exit = 110.0,
            costs = TradeForwardEngine.CostConfig(quantity = 0.0)
        )
        assertTrue(r.isFailure)
    }
}
