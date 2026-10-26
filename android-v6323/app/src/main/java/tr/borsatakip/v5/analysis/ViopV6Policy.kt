package tr.borsatakip.v5.analysis

import kotlin.math.abs
import kotlin.math.floor
import tr.borsatakip.v5.model.SignalValidity
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.ViopOpportunity

/**
 * V6: fail-closed, manual-only assessment. Quality points are NOT a win probability.
 * Never sends orders or upgrades a provider-unverified opportunity to VALID.
 */
object ViopV6Policy {
    const val CAPITAL_TRY = 100_000.0
    const val RISK_PER_TRADE_TRY = 250.0
    const val DAILY_LOSS_CEILING_TRY = 1_000.0
    const val TOTAL_OPEN_RISK_TRY = 500.0
    const val MAX_MARGIN_USAGE_TRY = 25_000.0
    const val MAX_ACTIVE_POSITIONS = 2
    const val MIN_QUALITY = 85
    const val MAX_ACCEPTED_RISK_SCORE = 60
    const val MAX_PER_SIDE = 3

    data class SizeDecision(
        val contracts: Int,
        val plannedRiskTry: Double,
        val reason: String
    )
    data class Assessment(
        val quality: Int,
        val eligible: Boolean,
        val reason: String,
        val isLong: Boolean
    )

    /** Conservative, rounded-down sizing, including estimated all-in cost PER contract.
     * Stop slippage can exceed the estimate. marginPerContract must be authoritative.
     */
    fun size(
        entry: Double, stop: Double, multiplier: Double?,
        costPerContract: Double?, marginPerContract: Double?,
        openRiskTry: Double = 0.0, dailyLossTry: Double = 0.0,
        activePositions: Int = 0,
        usedMarginTry: Double = 0.0,
        direction: String = "LONG"
    ): SizeDecision {
        if (!entry.isFinite() || !stop.isFinite() || entry <= 0 || stop <= 0 ||
            multiplier == null || !multiplier.isFinite() || multiplier <= 0 ||
            costPerContract == null || !costPerContract.isFinite() || costPerContract < 0 ||
            marginPerContract == null || !marginPerContract.isFinite() || marginPerContract <= 0
        ) return SizeDecision(0, 0.0, "RISK_INPUT_UNVERIFIED")
        if (!usedMarginTry.isFinite() || usedMarginTry < 0 || !openRiskTry.isFinite() || openRiskTry < 0 || !dailyLossTry.isFinite() ||
            dailyLossTry < 0 || activePositions < 0
        ) return SizeDecision(0, 0.0, "RISK_STATE_INVALID")
        if (activePositions >= MAX_ACTIVE_POSITIONS || dailyLossTry >= DAILY_LOSS_CEILING_TRY)
            return SizeDecision(0, 0.0, "RISK_CIRCUIT_BREAKER")
        // Price direction must be checked BEFORE costs; a stop equal to entry is never valid.
        val side = direction.uppercase(java.util.Locale.ROOT)
        val validStop = when (side) {
            "LONG" -> stop < entry
            "SHORT" -> stop > entry
            else -> false
        }
        if (!validStop) return SizeDecision(0, 0.0, "INVALID_STOP_DIRECTION")
        if (usedMarginTry >= MAX_MARGIN_USAGE_TRY) return SizeDecision(0, 0.0, "MARGIN_LIMIT_REACHED")
        val perContract = abs(entry - stop) * multiplier + costPerContract
        if (perContract <= 0 || !perContract.isFinite())
            return SizeDecision(0, 0.0, "ZERO_STOP_DISTANCE")
        val riskAvailable = minOf(RISK_PER_TRADE_TRY, TOTAL_OPEN_RISK_TRY - openRiskTry,
            DAILY_LOSS_CEILING_TRY - dailyLossTry)
        val n = minOf(floor(riskAvailable / perContract), floor((MAX_MARGIN_USAGE_TRY - usedMarginTry) / marginPerContract))
            .takeIf { it.isFinite() && it >= 1.0 && it <= Int.MAX_VALUE }?.toInt() ?: 0
        return SizeDecision(n, n * perContract, if (n > 0) "MANUAL_CONFIRMATION_REQUIRED" else "NO_CONTRACT_FITS_LIMITS")
    }

    data class EntryCheck(
        val valid: Boolean,
        val reason: String,
        val grossRr: Double?,
        val netRr: Double? = null
    )

    /** Reprice the last CLOSED-bar plan using current executable bid or ask.
     * Without an independently verified *round-trip* costPerContractTry, this is
     * GROSS screening only. No cost estimate is invented or assumed to be zero.
     * Stop slippage beyond the supplied round-trip estimate remains an open risk.
     */
    fun currentEntry(x: ViopOpportunity, costPerContractTry: Double? = null): EntryCheck {
        val p = x.riskPlan ?: return EntryCheck(false, "ENTRY_PLAN_UNVERIFIED", null)
        val isLong = x.direction.equals("LONG", true)
        if (!isLong && !x.direction.equals("SHORT", true))
            return EntryCheck(false, "NO_DIRECTION", null)
        val current = (if (isLong) x.quote.ask else x.quote.bid)
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return EntryCheck(false, "EXECUTABLE_PRICE_MISSING", null)
        val stop = p.stop?.takeIf { it.isFinite() && it > 0.0 }
            ?: return EntryCheck(false, "STOP_MISSING", null)
        val target = p.target1?.takeIf { it.isFinite() && it > 0.0 }
            ?: return EntryCheck(false, "TARGET_MISSING", null)
        if ((isLong && (stop >= current || target <= current)) ||
            (!isLong && (stop <= current || target >= current)))
            return EntryCheck(false, "LIVE_PRICE_INVALIDATES_PLAN", null)
        val risk = kotlin.math.abs(current - stop)
        val reward = kotlin.math.abs(target - current)
        val grossRr = reward / risk
        if (!grossRr.isFinite() || grossRr + 1e-9 < ViopRiskRewardPolicy.MIN_TARGET1_GROSS_RR)
            return EntryCheck(false, "LIVE_RR_TOO_LOW", grossRr)
        if (costPerContractTry == null) return EntryCheck(true, "GROSS_RR_ONLY_COSTS_UNVERIFIED", grossRr)
        if (!costPerContractTry.isFinite() || costPerContractTry < 0.0)
            return EntryCheck(false, "COST_INPUT_INVALID", grossRr)
        val multiplier = x.contract.multiplier?.takeIf { it.isFinite() && it > 0.0 }
            ?: return EntryCheck(false, "CONTRACT_MULTIPLIER_MISSING", grossRr)
        val netRisk = risk * multiplier + costPerContractTry
        val netReward = reward * multiplier - costPerContractTry
        val netRr = if (netRisk > 0.0) netReward / netRisk else Double.NaN
        if (!netRr.isFinite() || netRr + 1e-9 < ViopRiskRewardPolicy.MIN_TARGET1_GROSS_RR)
            return EntryCheck(false, "LIVE_NET_RR_TOO_LOW", grossRr, netRr.takeIf { it.isFinite() })
        return EntryCheck(true, "NET_RR_CHECKED_MANUAL_ONLY", grossRr, netRr)
    }

    fun assess(x: ViopOpportunity, nowMs: Long = System.currentTimeMillis()): Assessment {
        val c = x.contract
        val q = x.quote
        val isLong = x.direction.equals("LONG", true)
        val sideKnown = isLong || x.direction.equals("SHORT", true)
        // Weighted evidence must be available. No inferred 1h/15m/5m candles.
        val mtf = (x.mtfCompletenessPct.coerceIn(0, 100) * 25 / 100)
        val momentum = ((if (x.breakoutConfirmed) 12 else 0) +
            (x.technicalScore.coerceIn(0, 100) * 8 / 100))
        val spread = if (q.bid != null && q.ask != null &&
            q.bid.isFinite() && q.ask.isFinite() && q.bid > 0 &&
            q.ask >= q.bid && (q.ask - q.bid) / q.bid <= 0.003) 10 else 0
        val liquidity = x.liquidityScore.coerceIn(0, 100) * 10 / 100 + spread
        val activity = if (q.volume != null && q.volume.isFinite() && q.volume > 0.0 &&
            (q.openInterest ?: 0L) > 0L) 15 else 0
        val risk = (100 - x.riskScore).coerceIn(0, 100) * 15 / 100
        val expiry = if (c.lastTradingAt != null && c.lastTradingAt > nowMs) 5 else 0
        val score = (mtf + momentum + liquidity + activity + risk + expiry).coerceIn(0, 100)
        val reason = when {
            x.validity != SignalValidity.VALID || x.decisionState != DecisionState.VERIFIED_OPPORTUNITY ->
                "UNVERIFIED_SIGNAL"
            !sideKnown -> "NO_DIRECTION"
            !q.realtime || !q.currentSessionIncluded || !q.price.isFinite() || q.price <= 0.0 ||
                q.delaySeconds != 0 || q.exchangeTimestamp <= 0L ||
                q.exchangeTimestamp > nowMs ||
                nowMs - q.exchangeTimestamp > 60_000L ||
                !q.symbol.equals(c.symbol, ignoreCase = true) ->
                "LIVE_DATA_NOT_VERIFIED"
            c.multiplier == null || !c.multiplier.isFinite() || c.multiplier <= 0 || c.lastTradingAt == null ->
                "CONTRACT_METADATA_MISSING"
            c.lastTradingAt <= nowMs -> "CONTRACT_EXPIRED"
            x.riskScore > MAX_ACCEPTED_RISK_SCORE -> "RISK_SCORE_TOO_HIGH"
            !currentEntry(x).valid -> currentEntry(x).reason
            x.mtfCompletenessPct < 100 -> "MTF_INCOMPLETE"
            spread == 0 -> "SPREAD_NOT_ACCEPTABLE"
            activity == 0 -> "LIQUIDITY_NOT_VERIFIED"
            score < MIN_QUALITY -> "LOW_QUALITY"
            else -> "MANUAL_REVIEW_ONLY"
        }
        return Assessment(score, reason == "MANUAL_REVIEW_ONLY", reason, isLong)
    }

    fun shortlist(items: List<ViopOpportunity>, nowMs: Long = System.currentTimeMillis()): List<ViopOpportunity> {
        val validated = items.map { it to assess(it, nowMs) }.filter { it.second.eligible }
            .sortedWith(compareByDescending<Pair<ViopOpportunity, Assessment>> { it.second.quality }
                .thenBy { it.first.contract.symbol })
        return validated.filter { it.second.isLong }.take(MAX_PER_SIDE).map { it.first } +
            validated.filterNot { it.second.isLong }.take(MAX_PER_SIDE).map { it.first }
    }
}
