package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.analysis.v531.V531Direction
import tr.borsatakip.v5.analysis.v539.V539RiskRewardGate
import tr.borsatakip.v5.analysis.v540.V540RankingEngine
import tr.borsatakip.v5.model.Candle
import tr.borsatakip.v5.model.RankingStatus
import tr.borsatakip.v5.model.RiskPlan
import tr.borsatakip.v5.model.TechnicalSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** V5.4.1 canonical finalization: final direction -> risk plan -> RR gate -> quality -> ranking. */
object V540FinalDecisionFlow {
    const val FLOW_VERSION = "V5.4.1-FINAL-FLOW-V2"

    data class Result(
        val engineDirection: V531Direction,
        val publishedDirection: V531Direction,
        val riskPlan: RiskPlan?,
        val rr1: Double?,
        val rr2: Double?,
        val rrGateCode: String,
        val timingStatus: String,
        val setupType: String,
        val qualityClass: String,
        val rankingScore: Int,
        val rankingStatus: RankingStatus,
        val mtfAvailableCount: Int,
        val mtfExpectedCount: Int,
        val mtfCompletenessPct: Int,
        val rankingAudit: List<String>
    )

    fun finalize(
        engineDirection: V531Direction,
        finalSignalScore: Int,
        dataConfidence: Int,
        riskScore: Int,
        price: Double,
        technical: TechnicalSnapshot,
        candles: List<Candle>,
        freshnessScore: Int,
        mtfAvailableCount: Int,
        mtfExpectedCount: Int = V540RankingEngine.EXPECTED_MTF_COUNT,
        marketRegimeAdjustment: Int = 0,
        // ViOP-only opt-in; BIST legacy risk plans retain their original target model.
        minFirstTargetGrossRr: Double = 0.0,
        stopAtrMultiplier: Double = 1.25
    ): Result {
        val rawPlan = buildRiskPlan(engineDirection, price, technical.atr14, technical.support, technical.resistance, candles, minFirstTargetGrossRr, stopAtrMultiplier)
        val rr1 = rawPlan?.rr1
        val rr2 = rawPlan?.rr2
        val rrGate = V539RiskRewardGate.evaluate(engineDirection, rr1)
        // Distinguish a valid legacy plan that cannot meet the *ViOP* gross T1 floor
        // from entirely missing input data (ATR/price/etc.). Do not publish either.
        val viopTargetInfeasible = minFirstTargetGrossRr > 0.0 && rawPlan == null &&
            engineDirection != V531Direction.WATCH &&
            buildRiskPlan(engineDirection, price, technical.atr14, technical.support, technical.resistance, candles) != null
        val effectiveRrGateCode = if (viopTargetInfeasible) "VIOP_T1_RR_NOT_FEASIBLE" else rrGate.code
        val mtfSufficient = mtfExpectedCount <= 0 || mtfAvailableCount >= V540RankingEngine.MIN_MTF_COUNT_FOR_RANKING
        val publishedDirection = if (mtfSufficient) rrGate.publishedDirection else V531Direction.WATCH
        val finalPlan = if (publishedDirection == V531Direction.WATCH) null else rawPlan
        val timing = timingStatus(publishedDirection, price, technical, candles)
        val setup = setupType(publishedDirection, price, technical, candles)
        val ranking = V540RankingEngine.evaluate(
            finalSignalScore = finalSignalScore,
            dataConfidence = dataConfidence,
            riskScore = riskScore,
            relativeVolume = technical.volumeRatio,
            freshnessScore = freshnessScore,
            rrGateCode = effectiveRrGateCode,
            published = publishedDirection != V531Direction.WATCH,
            mtfAvailableCount = mtfAvailableCount,
            mtfExpectedCount = mtfExpectedCount,
            marketRegimeAdjustment = marketRegimeAdjustment
        )
        val quality = qualityClass(
            publishedDirection,
            finalSignalScore,
            dataConfidence,
            riskScore,
            rr1,
            rr2,
            timing,
            ranking.mtfCompletenessPct
        )
        return Result(
            engineDirection = engineDirection,
            publishedDirection = publishedDirection,
            riskPlan = finalPlan,
            rr1 = rr1,
            rr2 = rr2,
            rrGateCode = if (!mtfSufficient) "MTF_INSUFFICIENT_BLOCK" else effectiveRrGateCode,
            timingStatus = timing,
            setupType = setup,
            qualityClass = quality,
            rankingScore = ranking.score,
            rankingStatus = ranking.status,
            mtfAvailableCount = mtfAvailableCount,
            mtfExpectedCount = mtfExpectedCount,
            mtfCompletenessPct = ranking.mtfCompletenessPct,
            rankingAudit = ranking.audit
        )
    }

    fun buildRiskPlan(
        direction: V531Direction,
        price: Double,
        atrRaw: Double?,
        supportRaw: Double?,
        resistanceRaw: Double?,
        candles: List<Candle>,
        minFirstTargetGrossRr: Double = 0.0,
        stopAtrMultiplier: Double = 1.25
    ): RiskPlan? {
        // A target is not actionable just because the minimum RR can be written down:
        // it must fit inside the existing 2.5 ATR extension and structural barriers.
        if (!minFirstTargetGrossRr.isFinite() || minFirstTargetGrossRr < 0.0 || !stopAtrMultiplier.isFinite() || stopAtrMultiplier <= 0) return null
        if (direction == V531Direction.WATCH || !price.isFinite() || price <= 0.0) return null
        val atr = atrRaw?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val support = supportRaw?.takeIf { it.isFinite() && it > 0.0 }
        val resistance = resistanceRaw?.takeIf { it.isFinite() && it > 0.0 }
        val structuralWindow = candles.dropLast(1).takeLast(20)
        val swingLow = structuralWindow.minOfOrNull { it.low }
        val swingHigh = structuralWindow.maxOfOrNull { it.high }

        return when (direction) {
            V531Direction.LONG -> {
                val structuralStopLevel = listOfNotNull(support, swingLow).filter { it < price }.maxOrNull()
                val modelStop = price - stopAtrMultiplier * atr
                val stop = min(modelStop, structuralStopLevel?.minus(0.35 * atr) ?: modelStop)
                if (!stop.isFinite() || stop >= price) return null

                val modelT1 = price + 1.5 * atr
                val modelT2 = price + 2.5 * atr
                val barrier = listOfNotNull(resistance, swingHigh).filter { it > price }.minOrNull()
                val risk = price - stop
                if (risk <= 0.0 || !risk.isFinite()) return null
                // Only raise ViOP T1 if it fits the ORIGINAL T2 extension and known overhead resistance.
                // No extra upside is invented past those limits; otherwise the plan is blocked.
                val requiredT1 = max(modelT1, price + minFirstTargetGrossRr * risk)
                val target2 = barrier?.let { min(modelT2, it) } ?: modelT2
                if (!requiredT1.isFinite() || (minFirstTargetGrossRr > 0.0 && target2 < requiredT1 - 1e-9)) return null
                val target1 = min(requiredT1, target2)
                if (!(target1 > price) || barrier?.let { target1 > it || target2 > it } == true) return null
                val rr1 = (target1 - price) / risk
                if (rr1 + 1e-9 < minFirstTargetGrossRr) return null
                RiskPlan(price, stop, target1, target2, rr1, (target2 - price) / risk)
            }
            V531Direction.SHORT -> {
                val structuralStopLevel = listOfNotNull(resistance, swingHigh).filter { it > price }.minOrNull()
                val modelStop = price + stopAtrMultiplier * atr
                val stop = max(modelStop, structuralStopLevel?.plus(0.35 * atr) ?: modelStop)
                if (!stop.isFinite() || stop <= price) return null

                val modelT1 = price - 1.5 * atr
                val modelT2 = price - 2.5 * atr
                val barrier = listOfNotNull(support, swingLow).filter { it < price }.maxOrNull()
                val risk = stop - price
                if (risk <= 0.0 || !risk.isFinite()) return null
                // SHORT is symmetrical; nearby support is an uncrossable known target barrier.
                val requiredT1 = min(modelT1, price - minFirstTargetGrossRr * risk)
                val target2 = barrier?.let { max(modelT2, it) } ?: modelT2
                if (!requiredT1.isFinite() || (minFirstTargetGrossRr > 0.0 && target2 > requiredT1 + 1e-9)) return null
                val target1 = max(requiredT1, target2)
                if (!(target1 < price) || barrier?.let { target1 < it || target2 < it } == true) return null
                val rr1 = (price - target1) / risk
                if (rr1 + 1e-9 < minFirstTargetGrossRr) return null
                RiskPlan(price, stop, target1, target2, rr1, (price - target2) / risk)
            }
            V531Direction.WATCH -> null
        }
    }

    private fun qualityClass(
        direction: V531Direction,
        score: Int,
        confidence: Int,
        risk: Int,
        rr1: Double?,
        rr2: Double?,
        timing: String,
        mtfCompletenessPct: Int
    ): String = when {
        direction == V531Direction.WATCH -> "İZLE"
        timing == "GEÇ KALINMIŞ" -> "İZLE"
        // Multi-target quality: RR1 is the conservative first objective; RR2 captures extension potential.
        // Do not require an RR1 value that the canonical 1.25 ATR stop / 1.5 ATR T1 model cannot reach.
        score >= 85 && confidence >= 75 && (rr1 ?: 0.0) >= 1.20 && (rr2 ?: 0.0) >= 2.0 && risk <= 40 && mtfCompletenessPct >= 80 -> "A SINIFI"
        score >= 70 && confidence >= 60 && (rr1 ?: 0.0) >= 1.0 && (rr2 ?: 0.0) >= 1.5 && risk <= 60 && mtfCompletenessPct >= 50 -> "B SINIFI"
        score >= 55 && (rr1 ?: 0.0) >= 1.0 && risk <= 75 -> "İZLE"
        else -> "RED"
    }

    private fun timingStatus(direction: V531Direction, price: Double, t: TechnicalSnapshot, candles: List<Candle>): String {
        if (direction == V531Direction.WATCH) return "DEĞERLENDİRİLMEDİ"
        val atr = t.atr14?.takeIf { it.isFinite() && it > 0.0 }
        val ema20 = t.ema20?.takeIf { it.isFinite() }
        val ret5 = if (candles.size >= 6) pct(candles[candles.lastIndex - 5].close, candles.last().close) else null
        return when {
            atr != null && ema20 != null && direction == V531Direction.LONG && price > ema20 + 2.2 * atr -> "GEÇ KALINMIŞ"
            atr != null && ema20 != null && direction == V531Direction.SHORT && price < ema20 - 2.2 * atr -> "GEÇ KALINMIŞ"
            ret5 != null && abs(ret5) >= 10.0 -> "GEÇ KALINMIŞ"
            ret5 != null && abs(ret5) >= 7.0 -> "SINIRDA"
            else -> "ZAMANINDA"
        }
    }

    private fun setupType(direction: V531Direction, price: Double, t: TechnicalSnapshot, candles: List<Candle>): String {
        if (direction == V531Direction.WATCH) return "TEKNİK İZLEME"
        val atr = t.atr14?.takeIf { it.isFinite() && it > 0.0 }
        val volumeRatio = t.volumeRatio?.takeIf { it.isFinite() && it >= 0.0 }
        val breakout = BreakoutVolumeEngine.evaluate(candles, volumeRatio, price, atr, 20, 0.0015, 0.12)
        if (direction == V531Direction.LONG && breakout.state == "YUKARI KIRILIM") return if (breakout.confirmed) "KIRILIM" else "KIRILIM • TEYİT BEKLİYOR"
        if (direction == V531Direction.SHORT && breakout.state == "AŞAĞI KIRILIM") return if (breakout.confirmed) "KIRILIM" else "KIRILIM • TEYİT BEKLİYOR"
        val ema20 = t.ema20
        val ema50 = t.ema50
        val ema200 = t.ema200
        val emaLong = ema20 != null && ema50 != null && ema200 != null && price > ema20 && ema20 > ema50 && ema50 > ema200
        val emaShort = ema20 != null && ema50 != null && ema200 != null && price < ema20 && ema20 < ema50 && ema50 < ema200
        if ((direction == V531Direction.LONG && emaLong) || (direction == V531Direction.SHORT && emaShort)) return "TREND"
        if (breakout.confirmed) return "HACİM DESTEKLİ MOMENTUM"
        return "TEKNİK İZLEME"
    }

    private fun pct(from: Double, to: Double): Double? = if (from.isFinite() && to.isFinite() && from > 0.0) ((to / from) - 1.0) * 100.0 else null
}
