package tr.borsatakip.v5.analysis.v536

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.v531.RemoteEngineContract
import tr.borsatakip.v5.analysis.v531.RemoteEngineMetadata
import tr.borsatakip.v5.analysis.v531.ScoringConfig
import tr.borsatakip.v5.analysis.v540.V540CalculationOutputSchema
import tr.borsatakip.v5.analysis.v533.V533AuditHash

class V536OutputHashTest {
    private fun valid(): RemoteEngineMetadata {
        val base = RemoteEngineMetadata(
            calculationEngineVersion = ScoringConfig.ENGINE_VERSION,
            engineMode = "REMOTE",
            finalSignalScore = 80,
            longScore = 80,
            shortScore = 20,
            dataConfidenceScore = 90,
            riskScore = 25,
            conflictPenalty = 0,
            signalAvailableWeight = 100,
            mtfConsensusScore = 80,
            mtfAvailableCount = 3, // A consensus score requires >=3 MTF frames.
            dominantDirection = "LONG",
            direction = "LONG",
            decisionState = "VERIFIED_OPPORTUNITY",
            signalValidity = "VALID",
            reasonCodes = listOf("LONG_SIGNAL"),
            scoreBreakdown = listOf(
                "ENGINE=${ScoringConfig.ENGINE_VERSION}",
                "RAW_LONG=80",
                "RAW_SHORT=20",
                "CONFLICT_PENALTY=0",
                "FINAL_SCORE=80",
                "CONFIDENCE=90",
                "DECISION=LONG"
            ),
            calculationInputHash = "a".repeat(64),
            calculationOutputHash = "0".repeat(64),
            calculationOutputSchema = V540CalculationOutputSchema.SCHEMA,
            qualityClass = "A",
            timingStatus = "READY",
            marketRegime = "TREND",
            marketRegimeConfidence = 75,
            breakoutDirection = "UP",
            openInterestChangePct = 1.2,
            spreadPct = 0.1,
            liquidityScore = 88,
            slippageSensitivity = 0.2,
            structureDistanceAtr = 0.6,
            contractRiskPct = 20,
            mtfConsensusLabel = "MTF LONG UYUM"
        )
        return base.copy(calculationOutputHash = V533AuditHash.calculationOutputHash(base))
    }

    @Test fun validOutputHashAccepted() {
        val check = RemoteEngineContract.validate(valid())
        assertTrue("Violations: ${check.violations}", check.accepted)
    }

    @Test fun qualityClassMutationInvalidatesHash() {
        assertFalse(RemoteEngineContract.isAccepted(valid().copy(qualityClass = "B")))
    }

    @Test fun regimeMutationInvalidatesHash() {
        assertFalse(RemoteEngineContract.isAccepted(valid().copy(marketRegime = "RANGE")))
    }

    @Test fun spreadMutationInvalidatesHash() {
        assertFalse(RemoteEngineContract.isAccepted(valid().copy(spreadPct = 0.3)))
    }

    @Test fun timingMutationInvalidatesHash() {
        assertFalse(RemoteEngineContract.isAccepted(valid().copy(timingStatus = "LATE")))
    }

    @Test fun wrongOutputSchemaRejected() {
        val m = valid().copy(calculationOutputSchema = "OLD")
        assertFalse(RemoteEngineContract.isAccepted(m))
    }
}
