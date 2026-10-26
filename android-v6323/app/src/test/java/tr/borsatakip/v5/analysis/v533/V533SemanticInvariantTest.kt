package tr.borsatakip.v5.analysis.v533

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.v531.RemoteEngineContract
import tr.borsatakip.v5.analysis.v531.RemoteEngineMetadata
import tr.borsatakip.v5.analysis.v531.ScoringConfig
import tr.borsatakip.v5.analysis.v531.SignalDecisionEngine
import tr.borsatakip.v5.analysis.v531.V531Direction

class V533SemanticInvariantTest {
    @Test fun validLongAccepted() = accept(fromDecision(82, 20, 92, 85, 0))
    @Test fun validShortAccepted() = accept(fromDecision(20, 82, 92, 85, 0))
    @Test fun lowConfidenceWatchAccepted() = accept(fromDecision(90, 10, 39, 100, 0))
    @Test fun degradedConfidence75Accepted() = accept(fromDecision(75, 10, 79, 100, 0))
    @Test fun degradedConfidence74IsWatchAccepted() = accept(fromDecision(74, 10, 79, 100, 0))
    @Test fun coverage64IsWatchAccepted() = accept(fromDecision(90, 10, 90, 64, 0))
    @Test fun margin5IsWatchAccepted() = accept(fromDecision(80, 75, 90, 100, 0))
    @Test fun severeMtfConflictIsWatchAccepted() = accept(fromDecision(90, 10, 90, 95, -55))

    @Test fun finalScoreTooHighRejected() = reject(rehash(valid().copy(finalSignalScore = 95)))
    @Test fun finalScoreTooLowRejected() = reject(rehash(valid().copy(finalSignalScore = 20)))
    @Test fun longShortDirectionMismatchRejected() = reject(rehash(valid().copy(dominantDirection = "SHORT")))
    @Test fun verifiedWatchDirectionRejected() = reject(rehash(valid().copy(direction = "WATCH")))
    @Test fun wrongConflictPenaltyRejected() = reject(rehash(fromDecision(90, 10, 90, 100, -50).copy(conflictPenalty = 0)))
    @Test fun confidenceBelow60CannotBeVerified() = reject(rehash(valid().copy(dataConfidenceScore = 59)))
    @Test fun coverageBelow65CannotBeVerified() = reject(rehash(valid().copy(signalAvailableWeight = 64)))
    @Test fun marginBelow6CannotBeVerified() = reject(rehash(valid().copy(longScore = 80, shortScore = 75, finalSignalScore = 80)))
    @Test fun score69CannotBeVerifiedAtNormalConfidence() = reject(rehash(valid().copy(longScore = 69, shortScore = 10, finalSignalScore = 69)))
    @Test fun score74CannotBeVerifiedAtDegradedConfidence() = reject(rehash(valid().copy(longScore = 74, shortScore = 10, finalSignalScore = 74, dataConfidenceScore = 79)))
    @Test fun missingDecisionReasonRejected() = reject(rehash(valid().copy(reasonCodes = emptyList())))
    @Test fun blankReasonRejected() = reject(rehash(valid().copy(reasonCodes = valid().reasonCodes + "")))
    @Test fun emptyBreakdownRejected() = reject(rehash(valid().copy(scoreBreakdown = emptyList())))
    @Test fun missingEngineBreakdownRejected() = reject(rehash(valid().copy(scoreBreakdown = valid().scoreBreakdown.filterNot { it.startsWith("ENGINE=") })))
    @Test fun missingFinalBreakdownRejected() = reject(rehash(valid().copy(scoreBreakdown = valid().scoreBreakdown.filterNot { it.startsWith("FINAL_SCORE=") })))
    @Test fun staleFinalBreakdownValueRejected() = reject(rehash(valid().copy(scoreBreakdown = valid().scoreBreakdown.map { if (it.startsWith("FINAL_SCORE=")) "FINAL_SCORE=1" else it })))
    @Test fun unknownReasonCodeRejected() = reject(rehash(valid().copy(reasonCodes = valid().reasonCodes + "MADE_UP_REASON")))
    @Test fun contradictoryExtraKnownReasonRejected() = reject(rehash(valid().copy(reasonCodes = valid().reasonCodes + "SHORT_SIGNAL")))
    @Test fun duplicateReasonRejected() = reject(rehash(valid().copy(reasonCodes = valid().reasonCodes + valid().reasonCodes.first())))
    @Test fun duplicateBreakdownKeyRejected() = reject(rehash(valid().copy(scoreBreakdown = valid().scoreBreakdown + "FINAL_SCORE=0")))
    @Test fun badInputHashRejected() = reject(valid().copy(calculationInputHash = "1234"))
    @Test fun badOutputHashRejected() = reject(valid().copy(calculationOutputHash = "1234"))
    @Test fun outputHashTamperRejected() = reject(valid().copy(calculationOutputHash = V533AuditHash.sha256Hex("tamper")))
    @Test fun identicalInputOutputHashRejected() { val x = valid(); reject(x.copy(calculationInputHash = x.calculationOutputHash)) }
    @Test fun invalidMtfRangeRejected() = reject(rehash(valid().copy(mtfConsensusScore = 101)))
    @Test fun invalidPenaltyRangeRejected() = reject(rehash(valid().copy(conflictPenalty = ScoringConfig.MTF_CONFLICT_PENALTY_MAX + 1)))
    @Test fun invalidDirectionEnumRejected() = reject(rehash(valid().copy(direction = "BUY")))
    @Test fun invalidDominantDirectionEnumRejected() = reject(rehash(valid().copy(dominantDirection = "BUY")))
    @Test fun invalidDecisionStateRejected() = reject(rehash(valid().copy(decisionState = "GO")))
    @Test fun invalidValidityRejected() = reject(rehash(valid().copy(signalValidity = "OK")))
    @Test fun validSignalMarkedWatchRejected() = reject(rehash(valid().copy(decisionState = "WATCH")))
    @Test fun watchMarkedValidRejected() { val x = fromDecision(90,10,39,100,0); reject(rehash(x.copy(signalValidity = "VALID"))) }

    private fun valid() = fromDecision(82, 18, 91, 85, 0)
    private fun accept(x: RemoteEngineMetadata) = assertTrue(RemoteEngineContract.validate(x).violations.joinToString(), RemoteEngineContract.isAccepted(x))
    private fun reject(x: RemoteEngineMetadata) = assertFalse(RemoteEngineContract.isAccepted(x))

    private fun fromDecision(long: Int, short: Int, confidence: Int, coverage: Int, mtf: Int?): RemoteEngineMetadata {
        val e = SignalDecisionEngine.evaluate(long, short, confidence, coverage, mtf)
        val verified = e.decision != V531Direction.WATCH
        val base = RemoteEngineMetadata(
            calculationEngineVersion = ScoringConfig.ENGINE_VERSION,
            engineMode = "REMOTE",
            finalSignalScore = e.finalScore,
            longScore = e.longScore,
            shortScore = e.shortScore,
            dataConfidenceScore = confidence,
            riskScore = 25,
            conflictPenalty = e.conflictPenalty,
            signalAvailableWeight = coverage,
            mtfConsensusScore = mtf,
            mtfAvailableCount = 6,
            mtfExpectedCount = 6,
            dominantDirection = e.dominantDirection.name,
            direction = e.decision.name,
            decisionState = if (verified) "VERIFIED_OPPORTUNITY" else "WATCH",
            signalValidity = if (verified) "VALID" else "WATCH",
            reasonCodes = e.reasonCodes.map { it.name },
            scoreBreakdown = listOf(
                "ENGINE=${ScoringConfig.ENGINE_VERSION}", "RAW_LONG=${e.longScore}", "RAW_SHORT=${e.shortScore}",
                "CONFLICT_PENALTY=${e.conflictPenalty}", "FINAL_SCORE=${e.finalScore}",
                "CONFIDENCE=$confidence", "DECISION=${e.decision.name}"
            ),
            calculationInputHash = V533AuditHash.sha256Hex("input-$long-$short-$confidence-$coverage-$mtf"),
            calculationOutputHash = ""
        )
        return rehash(base)
    }

    private fun rehash(x: RemoteEngineMetadata) = x.copy(calculationOutputHash = V533AuditHash.calculationOutputHash(x))
}
