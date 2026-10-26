package tr.borsatakip.v5.analysis

import tr.borsatakip.v5.model.DecisionState
import tr.borsatakip.v5.model.Opportunity
import tr.borsatakip.v5.model.SignalValidity

/**
 * Yayın güvenliği katmanı.
 * Gerçek zaman bütünlüğünden geçmeyen teknik analizler işlem sinyali gibi yayınlanmaz.
 */
object OpportunityPublicationPolicy {
    /** The last gate, after MTF and strategy optimization: WATCH rows never have executable fields. */
    fun enforceVerifiedPublication(item: Opportunity): Opportunity {
        val published = item.signalValidity == SignalValidity.VALID &&
            item.decisionState == DecisionState.VERIFIED_OPPORTUNITY &&
            item.direction.uppercase(java.util.Locale.ROOT) in setOf("LONG", "SHORT") &&
            item.isRealtime && item.dataMode == tr.borsatakip.v5.model.DataMode.REALTIME &&
            item.publicationAssessment?.eligible == true && item.riskPlan != null
        if (published) return item
        return item.copy(
            direction = if (item.dataMode == tr.borsatakip.v5.model.DataMode.DELAYED ||
                item.dataMode == tr.borsatakip.v5.model.DataMode.EOD) "İZLEME" else "NEUTRAL",
            signalValidity = if (item.signalValidity == SignalValidity.VALID) SignalValidity.WATCH else item.signalValidity,
            decisionState = when (item.decisionState) {
                DecisionState.INSUFFICIENT_DATA, DecisionState.REJECTED -> item.decisionState
                else -> DecisionState.WATCH
            },
            finalSignalScore = 0, signalScore = 0, longScore = 0, shortScore = 0,
            rankingScore = 0,
            rankingStatus = tr.borsatakip.v5.model.RankingStatus.INVALID,
            riskPlan = null,
            snapshot = item.snapshot?.copy(finalSignalScore = 0),
            scoreBreakdown = item.scoreBreakdown + "PUB_GATE=WATCH_ONLY_NON_VERIFIED"
        )
    }
    fun delayedObservation(opportunity: Opportunity, integrityReason: String): Opportunity {
        val note = "GECİKMELİ/GÜNLÜK VERİ • AL/SAT SİNYALİ ÜRETİLMEDİ. $integrityReason"
        return opportunity.copy(
            direction = "İZLEME",
            finalSignalScore = 0,
            longScore = 0,
            shortScore = 0,
            decisionState = DecisionState.WATCH,
            signalValidity = SignalValidity.WATCH,
            signalValidityReason = note,
            setupType = "GECİKMELİ TEKNİK ANALİZ",
            qualityClass = "İZLEME",
            riskPlan = null,
            snapshot = opportunity.snapshot?.copy(finalSignalScore = 0),
            scoreBreakdown = buildList {
                add("MODE=DELAYED_ANALYSIS")
                add("NO_TRADE_SIGNAL=true")
                add("RISK_NOTE=$note")
                addAll(
                    opportunity.scoreBreakdown.filterNot {
                        it.startsWith("DECISION_STATE=") ||
                            it.startsWith("MODE=DELAYED_ANALYSIS") ||
                            it.startsWith("NO_TRADE_SIGNAL=") ||
                            it.startsWith("RISK_NOTE=") ||
                            it.startsWith("LONG_SCORE=") ||
                            it.startsWith("SHORT_SCORE=") ||
                            it.startsWith("CLASS=") ||
                            it.startsWith("SETUP=") ||
                            it.startsWith("Toplam:")
                    }
                )
            }
        )
    }
}
