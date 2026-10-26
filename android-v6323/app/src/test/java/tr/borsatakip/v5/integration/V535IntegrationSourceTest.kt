package tr.borsatakip.v5.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V535IntegrationSourceTest {
    private fun source(path: String) = File(path).readText()

    @Test fun remoteUsesStrictV535SchemaAndHash() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("V535RemoteInputHash.SCHEMA"))
        assertTrue(s.contains("strictKeySet() != V535RemoteInputHash.REQUIRED_FIELDS"))
        assertTrue(s.contains("V535RemoteInputHash.hash(canonicalInput)"))
        assertFalse(s.contains("V534RemoteInputHash.hash(canonicalInput)"))
    }

    @Test fun allCanonicalRiskFieldsCrossChecked() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("V535RemoteInputConsistency.validate(canonicalInput, topLevelInputs)"))
        listOf("openInterestChangePct", "spreadPct", "liquidityScore", "slippageSensitivity", "structureDistanceAtr", "contractRiskPct")
            .forEach { assertTrue("missing top-level field $it", s.contains("strictNullable") && s.contains("\"$it\"")) }
    }

    @Test fun mtfLabelIsValidatedAndDerivedCanonically() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("V535MtfSemantics.isConsistent"))
        assertTrue(s.contains("mtfConsensusLabel = V535MtfSemantics.labelFor"))
    }

    @Test fun signalAndRankingScoresAreExplicitlySeparated() {
        val models = source("src/main/java/tr/borsatakip/v5/model/Models.kt")
        val client = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(models.contains("val signalScore:Int = finalSignalScore"))
        assertTrue(models.contains("val rankingScore:Int = finalSignalScore"))
        assertTrue(client.contains("signalScore = finalSignalScore"))
        assertTrue(client.contains("rankingScore = canonicalRanking"))
    }

    @Test fun serverAuthoritativeClockContractIsUsed() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("V536ClockIntegrity.validateSnapshot"))
        assertTrue(s.contains("V536ClockIntegrity.validateItem"))
        assertTrue(s.contains("signalGeneratedAt = generatedAt"))
    }

    @Test fun remoteCanonicalRiskValuesAreRetainedOnOpportunity() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        listOf("openInterestChangePct", "spreadPct", "liquidityScore", "slippageSensitivity", "structureDistanceAtr", "contractRiskPct")
            .forEach { assertTrue("canonical field not retained: $it", s.contains("$it = canonicalInput.$it")) }
    }

    @Test fun attestationIsNotFakedWithoutBackendKey() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v535/V535ServerAttestation.kt")
        assertTrue(s.contains("NOT_CONFIGURED"))
        assertTrue(s.contains("V535ServerAttestationVerifier"))
        assertFalse(s.contains("hardcodedSecret"))
    }
}
