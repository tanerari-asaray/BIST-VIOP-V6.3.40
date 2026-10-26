package tr.borsatakip.v5.analysis.v539

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.v531.RemoteEngineMetadata
import tr.borsatakip.v5.analysis.v531.ScoringConfig
import tr.borsatakip.v5.analysis.v533.V533AuditHash

class V539RemoteOutputHashTest {
    private fun metadata(rr1: Double? = 2.0, rr2: Double? = 3.0) = RemoteEngineMetadata(
        calculationEngineVersion = ScoringConfig.ENGINE_VERSION,
        engineMode = "REMOTE",
        finalSignalScore = 80,
        longScore = 80,
        shortScore = 20,
        dataConfidenceScore = 90,
        riskScore = 30,
        conflictPenalty = 0,
        signalAvailableWeight = 100,
        mtfConsensusScore = null,
        dominantDirection = "LONG",
        direction = "LONG",
        decisionState = "VERIFIED_OPPORTUNITY",
        signalValidity = "VALID",
        reasonCodes = listOf("LONG_SIGNAL"),
        scoreBreakdown = listOf("ENGINE=${ScoringConfig.ENGINE_VERSION}", "RAW_LONG=80", "RAW_SHORT=20", "CONFLICT_PENALTY=0", "FINAL_SCORE=80", "CONFIDENCE=90", "DECISION=LONG"),
        calculationInputHash = "a".repeat(64),
        calculationOutputHash = "b".repeat(64),
        calculationOutputSchema = V539CalculationOutputSchema.SCHEMA,
        qualityClass = "B SINIFI",
        timingStatus = "ZAMANINDA",
        marketRegime = "UNKNOWN",
        marketRegimeConfidence = 0,
        breakoutDirection = "NONE",
        riskReward1 = rr1,
        riskReward2 = rr2
    )

    @Test fun rr1MutationChangesOutputHash() {
        assertNotEquals(V533AuditHash.calculationOutputHash(metadata(2.0)), V533AuditHash.calculationOutputHash(metadata(2.1)))
    }

    @Test fun rr2MutationChangesOutputHash() {
        assertNotEquals(V533AuditHash.calculationOutputHash(metadata(rr2 = 3.0)), V533AuditHash.calculationOutputHash(metadata(rr2 = 3.1)))
    }

    @Test fun schemaRejectsOutOfRangeRiskReward() {
        assertTrue(V539CalculationOutputSchema.validate("B SINIFI", "ZAMANINDA", "UNKNOWN", 0, "NONE", 21.0, 2.0).contains("OUTPUT_RR1_RANGE"))
    }
}
