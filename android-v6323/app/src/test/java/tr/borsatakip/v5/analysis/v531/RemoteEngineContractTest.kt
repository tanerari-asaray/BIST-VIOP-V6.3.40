package tr.borsatakip.v5.analysis.v531

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.v533.V533AuditHash

class RemoteEngineContractTest {
    @Test fun validRemoteV533MetadataIsAccepted() = assertTrue(RemoteEngineContract.isAccepted(validMetadata()))
    @Test fun wrongEngineVersionIsRejected() = assertFalse(RemoteEngineContract.isAccepted(rehash(validMetadata().copy(calculationEngineVersion = "V5.3.2"))))
    @Test fun nonRemoteModeIsRejected() = assertFalse(RemoteEngineContract.isAccepted(rehash(validMetadata().copy(engineMode = "LOCAL"))))
    @Test fun invalidHashIsRejected() = assertFalse(RemoteEngineContract.isAccepted(validMetadata().copy(calculationOutputHash = "bad")))
    @Test fun emptyAuditIsRejectedForVerifiedResult() = assertFalse(RemoteEngineContract.isAccepted(rehash(validMetadata().copy(scoreBreakdown = emptyList()))))
    @Test fun validWatchMetadataIsAccepted() = assertTrue(RemoteEngineContract.isAccepted(fromDecision(90, 10, 39, 100, 0)))

    private fun validMetadata(): RemoteEngineMetadata = fromDecision(82, 18, 91, 85, 0)

    private fun fromDecision(long: Int, short: Int, confidence: Int, coverage: Int, mtf: Int?): RemoteEngineMetadata {
        val e = SignalDecisionEngine.evaluate(long, short, confidence, coverage, mtf)
        val verified = e.decision != V531Direction.WATCH
        val base = RemoteEngineMetadata(
            calculationEngineVersion = ScoringConfig.ENGINE_VERSION,
            engineMode = "REMOTE",
            finalSignalScore = e.finalScore,
            longScore = long,
            shortScore = short,
            dataConfidenceScore = confidence,
            riskScore = 24,
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
            scoreBreakdown = breakdown(long, short, e.finalScore, e.conflictPenalty, confidence, e.decision.name),
            calculationInputHash = V533AuditHash.sha256Hex("input-$long-$short-$confidence-$coverage-$mtf"),
            calculationOutputHash = ""
        )
        return rehash(base)
    }

    private fun rehash(x: RemoteEngineMetadata): RemoteEngineMetadata = x.copy(calculationOutputHash = V533AuditHash.calculationOutputHash(x))

    private fun breakdown(long: Int, short: Int, final: Int, penalty: Int, confidence: Int, decision: String) = listOf(
        "ENGINE=${ScoringConfig.ENGINE_VERSION}", "RAW_LONG=$long", "RAW_SHORT=$short",
        "CONFLICT_PENALTY=$penalty", "FINAL_SCORE=$final", "CONFIDENCE=$confidence", "DECISION=$decision"
    )
}
