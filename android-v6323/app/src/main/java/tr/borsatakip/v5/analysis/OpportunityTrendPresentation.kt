package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.DataMode
import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.RankingStatus
import tr.borsatakip.v5.model.SignalValidity

/** A technical trend is never proof of an executable trade. */
data class OpportunityTrendDisplay(
    val trend: TrendUiStyle,
    val verifiedSignal: Boolean,
    val signalLabel: String,
    val scoreLabel: String,
    val dataLabel: String
)

object OpportunityTrendPresentation {
    private const val MAX_REALTIME_AGE_MS = 60_000L

    fun from(opportunity: Opportunity, nowMs: Long = System.currentTimeMillis()): OpportunityTrendDisplay {
        val mode = opportunity.dataMode
        val exchangeTime = opportunity.exchangeTimestamp
        val fresh = exchangeTime > 0L && nowMs >= exchangeTime &&
            nowMs - exchangeTime <= MAX_REALTIME_AGE_MS
        val dir = opportunity.direction.trim().uppercase(java.util.Locale.ROOT)
        val verified = mode == DataMode.REALTIME && opportunity.isRealtime && fresh &&
            opportunity.signalValidity == SignalValidity.VALID &&
            opportunity.decisionState == DecisionState.VERIFIED_OPPORTUNITY &&
            opportunity.rankingStatus == RankingStatus.CALCULATED &&
            opportunity.rankingScore > 0 && (dir == "LONG" || dir == "SHORT")

        // Never infer a technical trend from dailyChangePct or a requested LONG/SHORT direction.
        val trend = if (mode == DataMode.UNVERIFIED || (mode == DataMode.REALTIME && !fresh)) {
            TrendUiPolicy.resolve(opportunity.price, opportunity.technical, DecisionState.INSUFFICIENT_DATA)
        } else {
            TrendUiPolicy.resolve(opportunity)
        }
        val dataLabel = when {
            mode == DataMode.UNVERIFIED -> "DOĞRULANMADI"
            mode == DataMode.DELAYED || mode == DataMode.EOD -> "GECİKMELİ"
            !fresh -> "ESKİ VERİ"
            verified -> "CANLI / ONAYLI"
            else -> "İZLEME"
        }
        val signalLabel = when {
            verified -> if (dir == "LONG") "▲ LONG" else "▼ SHORT"
            dataLabel == "GECİKMELİ" && trend.state in setOf(TrendUiState.BULLISH, TrendUiState.STRONG_BULLISH, TrendUiState.BEARISH, TrendUiState.STRONG_BEARISH) ->
                "GECİKMELİ • TEKNİK ${trend.arrow}"
            dataLabel == "GECİKMELİ" -> "GECİKMELİ • YÖN YOK"
            dataLabel == "DOĞRULANMADI" -> "DOĞRULANMADI"
            dataLabel == "ESKİ VERİ" -> "ESKİ VERİ • İŞLEM YOK"
            else -> "İZLEME • ${trend.label}"
        }
        return OpportunityTrendDisplay(
            trend = trend,
            verifiedSignal = verified,
            signalLabel = signalLabel,
            scoreLabel = if (verified) "${opportunity.rankingScore}/100" else "—",
            dataLabel = dataLabel
        )
    }
}
