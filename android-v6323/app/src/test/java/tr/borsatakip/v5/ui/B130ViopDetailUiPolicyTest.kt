package tr.borsatakip.v5.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.TechnicalSnapshot
import tr.borsatakip.v5.model.ViopContract
import tr.borsatakip.v5.model.ViopOpportunity
import tr.borsatakip.v5.model.ViopQuote

class B130ViopDetailUiPolicyTest {
    private val now = 1_800_000_000_000L

    @Test
    fun freshRealtimeQuoteIsLive() {
        val quote = quote(realtime = true, currentSession = true, delaySeconds = 1, exchangeTimestamp = now - 1_000L)
        assertEquals(ViopDetailDataState.LIVE, ViopDetailPresentationPolicy.dataState(quote, now))
    }

    @Test
    fun delayedQuoteIsNeverLabeledLive() {
        val quote = quote(realtime = false, currentSession = true, delaySeconds = 60, exchangeTimestamp = now - 2_000L)
        assertEquals(ViopDetailDataState.DELAYED, ViopDetailPresentationPolicy.dataState(quote, now))
    }

    @Test
    fun staleQuoteIsExplicitlyStale() {
        val quote = quote(realtime = true, currentSession = true, delaySeconds = 1, exchangeTimestamp = now - 120_000L)
        assertEquals(ViopDetailDataState.STALE, ViopDetailPresentationPolicy.dataState(quote, now))
    }

    @Test
    fun unverifiedOpportunityDoesNotExposeLongShort() {
        assertNull(ViopDetailPresentationPolicy.verifiedDirection(opportunity(SignalValidity.WATCH, DecisionState.WATCH, "LONG")))
    }

    @Test
    fun verifiedOpportunityExposesDirection() {
        assertEquals("SHORT", ViopDetailPresentationPolicy.verifiedDirection(opportunity(SignalValidity.VALID, DecisionState.VERIFIED_OPPORTUNITY, "SHORT")))
    }

    @Test
    fun fourHourTimeframeUsesRealHourlySourceAggregation() {
        assertEquals("60m", ViopDetailTimeframe.FOUR_HOUR.requestInterval)
        assertEquals(60, ViopDetailTimeframe.FOUR_HOUR.sourceMinutes)
        assertEquals(240, ViopDetailTimeframe.FOUR_HOUR.aggregateMinutes)
    }

    private fun quote(
        realtime: Boolean,
        currentSession: Boolean,
        delaySeconds: Int?,
        exchangeTimestamp: Long
    ) = ViopQuote(
        symbol = "F_FROTO0926",
        price = 100.0,
        bid = 99.9,
        ask = 100.1,
        dailyChangePct = 0.5,
        volume = 1000.0,
        openInterest = 200L,
        exchangeTimestamp = exchangeTimestamp,
        receivedAt = now,
        source = "test",
        realtime = realtime,
        delaySeconds = delaySeconds,
        currentSessionIncluded = currentSession
    )

    private fun opportunity(validity: SignalValidity, decisionState: DecisionState, direction: String): ViopOpportunity {
        val contract = ViopContract(
            symbol = "F_FROTO0926",
            underlying = "FROTO",
            expiry = "2026-09",
            validity = validity,
            validityReason = "test"
        )
        return ViopOpportunity(
            contract = contract,
            quote = quote(true, true, 1, now - 1_000L),
            candles = emptyList(),
            technical = TechnicalSnapshot(null, null, null, null, null, null, null, null, null, null, null, null, null),
            technicalScore = 80,
            riskScore = 20,
            liquidityScore = 70,
            expiryRisk = 10,
            longScore = 40,
            shortScore = 80,
            finalScore = 80,
            direction = direction,
            signalReason = "test",
            validity = validity,
            dataAgeMs = 1_000L,
            historyCandleCount = 220,
            decisionState = decisionState,
            dataConfidenceScore = 90
        )
    }
}
