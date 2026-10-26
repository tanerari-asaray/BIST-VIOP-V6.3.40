package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.*

class ViopDashboardPolicyTest {
    private val now = 1_800_000_000_000L
    private fun candidate(symbol: String = "F_LONG", direction: String = "LONG",
                          quoteAt: Long = now, expiryAt: Long = now + 86_400_000L): ViopOpportunity {
        val long = direction == "LONG"
        return ViopOpportunity(
            contract = ViopContract(symbol, "AKBNK", "2027-01", multiplier = 100.0, lastTradingAt = expiryAt),
            quote = ViopQuote(symbol, 100.0, 99.90, 100.0, null, 50_000.0, 1000L,
                quoteAt, now, "TEST", true, 0, true),
            candles = emptyList(),
            technical = TechnicalSnapshot(null,null,null,null,null,null,null,null,null,null,null,null,null),
            technicalScore = 100, riskScore = 20, liquidityScore = 100, expiryRisk = 0,
            longScore = if (long) 95 else 0, shortScore = if (long) 0 else 95,
            finalScore = 95, direction = direction, signalReason = "test", validity = SignalValidity.VALID,
            dataAgeMs = 0, historyCandleCount = 220, decisionState = DecisionState.VERIFIED_OPPORTUNITY,
            mtfCompletenessPct = 100, breakoutConfirmed = true,
            riskPlan = if (long) RiskPlan(100.0,99.0,103.0,105.0,3.0,5.0)
                else RiskPlan(100.0,101.0,96.0,95.0,4.0,5.0))
    }

    @Test fun freshRowsAndBothSideCountsUseSameInstant() {
        val state = ViopDashboardPolicy.snapshot(listOf(candidate(), candidate("F_SHORT", "SHORT")), now)
        assertEquals(2, state.items.size)
        assertEquals(1, state.longs)
        assertEquals(1, state.shorts)
        assertEquals(now, state.assessedAtMs)
    }

    @Test fun quoteExpiryRemovesRowAndCountTogether() {
        val raw = listOf(candidate())
        assertEquals(1, ViopDashboardPolicy.snapshot(raw, now + 60_000L).longs)
        val expired = ViopDashboardPolicy.snapshot(raw, now + 60_001L)
        assertTrue(expired.items.isEmpty())
        assertEquals(0, expired.longs)
        assertEquals(0, expired.shorts)
    }

    @Test fun contractExpiryRemovesOnlyExpiredSide() {
        val raw = listOf(candidate(expiryAt = now + 1000), candidate("F_SHORT", "SHORT"))
        val state = ViopDashboardPolicy.snapshot(raw, now + 1000)
        assertEquals(0, state.longs)
        assertEquals(1, state.shorts)
        assertEquals(listOf("F_SHORT"), state.items.map { it.contract.symbol })
    }

    @Test fun futureQuoteCannotProduceCountOrRow() {
        val state = ViopDashboardPolicy.snapshot(listOf(candidate(quoteAt = now + 1)), now)
        assertTrue(state.items.isEmpty())
        assertEquals(0, state.longs)
    }

    @Test fun rejectedCandidateDoesNotSurviveInCounts() {
        val raw = listOf(candidate().copy(validity = SignalValidity.REJECTED))
        val state = ViopDashboardPolicy.snapshot(raw, now)
        assertTrue(state.items.isEmpty())
        assertEquals(0, state.longs)
    }

    @Test fun limitsAndOrderingAreAppliedToRowsBeforeCounting() {
        val raw = (0..4).map { candidate("F_$it") }
        val state = ViopDashboardPolicy.snapshot(raw, now)
        assertEquals(ViopV6Policy.MAX_PER_SIDE, state.items.size)
        assertEquals(state.items.size, state.longs)
        assertEquals(listOf("F_0", "F_1", "F_2"), state.items.map { it.contract.symbol })
    }

    @Test fun invalidQuoteEvidenceCannotProduceVerifiedRowsOrSideCounts() {
        val base = candidate()
        listOf(base.quote.copy(currentSessionIncluded = false), base.quote.copy(price = Double.NaN),
            base.quote.copy(price = 0.0), base.quote.copy(volume = Double.POSITIVE_INFINITY)).forEach { quote ->
            val state = ViopDashboardPolicy.snapshot(listOf(base.copy(quote = quote)), now)
            assertTrue(state.items.isEmpty())
            assertEquals(0, state.longs)
            assertEquals(0, state.shorts)
        }
    }
}
