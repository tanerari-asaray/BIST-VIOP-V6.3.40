package tr.borsatakip.v5.analysis.v533

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.borsatakip.v5.analysis.v531.RemoteEngineMetadata
import tr.borsatakip.v5.analysis.v531.ScoringConfig

class V533AuditHashTest {
    @Test fun sha256IsDeterministic() = assertEquals(V533AuditHash.sha256Hex("abc"), V533AuditHash.sha256Hex("abc"))
    @Test fun sha256Has64HexCharacters() = assertTrue(V533AuditHash.isSha256Hex(V533AuditHash.sha256Hex("abc")))
    @Test fun differentInputChangesHash() = assertNotEquals(V533AuditHash.sha256Hex("abc"), V533AuditHash.sha256Hex("abd"))
    @Test fun outputScoreMutationChangesHash() { val a = validMetadata(); val b = a.copy(finalSignalScore = a.finalSignalScore - 1); assertNotEquals(V533AuditHash.calculationOutputHash(a), V533AuditHash.calculationOutputHash(b)) }
    @Test fun outputReasonMutationChangesHash() { val a = validMetadata(); val b = a.copy(reasonCodes = a.reasonCodes + "EXTRA"); assertNotEquals(V533AuditHash.calculationOutputHash(a), V533AuditHash.calculationOutputHash(b)) }
    @Test fun outputBreakdownMutationChangesHash() { val a = validMetadata(); val b = a.copy(scoreBreakdown = a.scoreBreakdown + "EXTRA=1"); assertNotEquals(V533AuditHash.calculationOutputHash(a), V533AuditHash.calculationOutputHash(b)) }
    @Test fun inputHashMutationChangesOutputHash() { val a = validMetadata(); val b = a.copy(calculationInputHash = V533AuditHash.sha256Hex("other-input")); assertNotEquals(V533AuditHash.calculationOutputHash(a), V533AuditHash.calculationOutputHash(b)) }
    @Test fun invalidShortHashRejectedByFormat() = assertFalse(V533AuditHash.isSha256Hex("abcd"))
    @Test fun hashCaseIsAcceptedByFormat() = assertTrue(V533AuditHash.isSha256Hex(V533AuditHash.sha256Hex("abc").uppercase()))

    private fun validMetadata() = RemoteEngineMetadata(
        calculationEngineVersion = ScoringConfig.ENGINE_VERSION,
        engineMode = "REMOTE",
        finalSignalScore = 82,
        longScore = 82,
        shortScore = 18,
        dataConfidenceScore = 91,
        riskScore = 25,
        conflictPenalty = 0,
        signalAvailableWeight = 85,
        mtfConsensusScore = null,
        dominantDirection = "LONG",
        direction = "LONG",
        decisionState = "VERIFIED_OPPORTUNITY",
        signalValidity = "VALID",
        reasonCodes = listOf("LONG_SIGNAL"),
        scoreBreakdown = listOf("ENGINE=${ScoringConfig.ENGINE_VERSION}","RAW_LONG=82","RAW_SHORT=18","CONFLICT_PENALTY=0","FINAL_SCORE=82","CONFIDENCE=91","DECISION=LONG"),
        calculationInputHash = V533AuditHash.sha256Hex("input"),
        calculationOutputHash = ""
    )
}
