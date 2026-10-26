package tr.borsatakip.v5.analysis

import org.junit.Assert.*
import org.junit.Test
import tr.borsatakip.v5.model.*

class ViopV6SafetyGateTest {
    private val now = 1800000000000L
    private fun candidate(
        exchangeAt: Long = now - 1000L,
        expiry: Long? = now + 86400000L,
        risk: Int = 20,
        cachedAge: Long = 1000L
    ): ViopOpportunity {
        val c = ViopContract(symbol = "F_TEST", underlying = "AKBNK", expiry = "2026-11",
            multiplier = 100.0, lastTradingAt = expiry)
        val q = ViopQuote("F_TEST", 100.0, 99.90, 100.0, null, 50000.0,
            1000L, exchangeAt, now, "UNIT", true, 0, true)
        val t = TechnicalSnapshot(null,null,null,null,null,null,null,null,null,null,null,null,null)
        return ViopOpportunity(
            contract = c, quote = q, candles = emptyList(), technical = t,
            technicalScore = 100, riskScore = risk, liquidityScore = 100,
            expiryRisk = 0, longScore = 95, shortScore = 0,
            finalScore = 95, direction = "LONG", signalReason = "unit",
            validity = SignalValidity.VALID, dataAgeMs = cachedAge,
            historyCandleCount = 220, decisionState = DecisionState.VERIFIED_OPPORTUNITY,
            mtfCompletenessPct = 100, breakoutConfirmed = true,
            riskPlan = RiskPlan(100.0, 99.0, 103.0, 105.0, 3.0, 5.0)
        )
    }

    @Test fun staleExchangeTimestampWinsOverCachedDataAge() {
        val result = ViopV6Policy.assess(candidate(exchangeAt = now - 181000L, cachedAge = 500L), now)
        assertEquals("LIVE_DATA_NOT_VERIFIED", result.reason)
        assertFalse(result.eligible)
    }
    @Test fun expiredContractFailsEvenAtHighQuality() {
        val result = ViopV6Policy.assess(candidate(expiry = now-1L), now)
        assertEquals("CONTRACT_EXPIRED", result.reason)
        assertFalse(result.eligible)
    }
    @Test fun missingExpiryIsFailClosed() {
        assertEquals("CONTRACT_METADATA_MISSING", ViopV6Policy.assess(candidate(expiry = null),now).reason)
    }
    @Test fun highRiskCannotPassQualityGate() {
        assertEquals("RISK_SCORE_TOO_HIGH", ViopV6Policy.assess(candidate(risk = 100), now).reason)
    }
    @Test fun freshQualityCandidateIsManualReviewOnly() {
        val decision = ViopV6Policy.assess(candidate(), now)
        assertEquals("MANUAL_REVIEW_ONLY", decision.reason)
        assertTrue(decision.eligible)
    }
    @Test fun zeroDistanceStopAlwaysRejectsWithCosts() {
        val size = ViopV6Policy.size(100.0, 100.0, 100.0, 5.0, 1000.0)
        assertEquals(0, size.contracts)
        assertEquals("INVALID_STOP_DIRECTION", size.reason)
    }
    @Test fun shortStopBelowEntryIsRejected() {
        assertEquals(0, ViopV6Policy.size(100.0, 99.0, 10.0, 1.0, 1000.0, direction="SHORT").contracts)
    }
    @Test fun reservedMarginReducesRemainingBudget() {
        assertEquals(1, ViopV6Policy.size(100.0, 99.0, 1.0, 1.0, 3000.0,
            usedMarginTry = 20000.0).contracts)
        assertEquals(0, ViopV6Policy.size(100.0, 99.0, 1.0, 1.0, 3000.0,
            usedMarginTry = 25000.0).contracts)
    }
    @Test fun currentAskCanInvalidateClosedBarTarget() {
        val x = candidate().let { it.copy(quote=it.quote.copy(ask=103.0)) }
        assertEquals("LIVE_PRICE_INVALIDATES_PLAN", ViopV6Policy.currentEntry(x).reason)
    }
    @Test fun missingCurrentAskCannotBeExecutableLong() {
        val x = candidate().let { it.copy(quote=it.quote.copy(ask=null)) }
        assertEquals("EXECUTABLE_PRICE_MISSING", ViopV6Policy.currentEntry(x).reason)
    }
    @Test fun grossRrAtFloorIsNotEnoughWhenRealCostsKnown() {
        val p = RiskPlan(100.0, 97.5, 103.75, 105.0, 1.5, 2.0)
        val c = candidate().copy(riskPlan = p)
        assertTrue(ViopV6Policy.currentEntry(c).valid)
        val priced = ViopV6Policy.currentEntry(c, costPerContractTry = 5.0)
        assertEquals("LIVE_NET_RR_TOO_LOW", priced.reason)
        assertFalse(priced.valid)
        assertTrue(priced.netRr!! < 1.5)
    }

    @Test fun independentlyKnownCostMustNotBeInventedAsZero() {
        val c = candidate().copy(riskPlan = RiskPlan(100.0, 97.5, 104.5, 105.0, 1.8, 2.0))
        assertEquals("GROSS_RR_ONLY_COSTS_UNVERIFIED", ViopV6Policy.currentEntry(c).reason)
        assertEquals("NET_RR_CHECKED_MANUAL_ONLY", ViopV6Policy.currentEntry(c, costPerContractTry=5.0).reason)
        assertEquals("COST_INPUT_INVALID", ViopV6Policy.currentEntry(c, costPerContractTry=-1.0).reason)
    }

    @Test fun currentEntryUsesBidForShortAndChecksRr() {
        val x = candidate().copy(direction="SHORT",
            riskPlan=RiskPlan(100.0,101.0,96.0,95.0,4.0,5.0))
        assertTrue(ViopV6Policy.currentEntry(x).valid)
    }


    @Test fun unverifiedCurrentSessionCannotPassQualityGate() {
        val base = candidate()
        val result = ViopV6Policy.assess(base.copy(quote = base.quote.copy(currentSessionIncluded = false)), now)
        assertEquals("LIVE_DATA_NOT_VERIFIED", result.reason)
        assertFalse(result.eligible)
    }

    @Test fun invalidLastPriceCannotPassWithAnOtherwiseValidExecutableAsk() {
        val base = candidate()
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            val result = ViopV6Policy.assess(base.copy(quote = base.quote.copy(price = value)), now)
            assertEquals("LIVE_DATA_NOT_VERIFIED", result.reason)
            assertFalse(result.eligible)
        }
    }

    @Test fun nonFiniteOrMissingVolumeCannotVerifyLiquidity() {
        val base = candidate()
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            val result = ViopV6Policy.assess(base.copy(quote = base.quote.copy(volume = value)), now)
            assertEquals("LIQUIDITY_NOT_VERIFIED", result.reason)
            assertFalse(result.eligible)
        }
    }
}
