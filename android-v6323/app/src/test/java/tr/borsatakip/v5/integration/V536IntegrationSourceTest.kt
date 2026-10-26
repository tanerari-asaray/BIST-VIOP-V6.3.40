package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V536IntegrationSourceTest {
    private fun source(path: String) = File(path).readText()

    @Test fun realtimeUsesEncodedQueryParameters() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("addQueryParameter(\"symbols\", symbolQuery)"))
        assertFalse(s.contains("append(\"&symbols=\$symbolQuery\")"))
    }

    @Test fun realtimeUsesTimeframeSpecificClockPolicy() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("V536ClockIntegrity.validateSnapshot"))
        assertTrue(s.contains("V536ClockIntegrity.validateItem"))
    }

    @Test fun remoteRequiresPublicKeyAttestation() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("V538ServerAttestationVerifier.verify"))
        assertTrue(s.contains("attestation.status != V538AttestationStatus.VERIFIED"))
    }

    @Test fun restAndWebsocketBothUseReplayProtection() {
        val rest = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        val ws = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerSocket.kt")
        assertTrue(rest.contains("validateReplay(parse(JSONObject(body), bootstrapTrustedNow, clientReceiveElapsed))"))
        assertTrue(ws.contains("parser.validateReplay(parser.parse(root, bootstrapTrustedNow, elapsedNow))"))
    }

    @Test fun releaseBuildRequiresAttestationKeys() {
        val s = source("build.gradle.kts")
        assertTrue(s.contains("BORSA_ATTESTATION_PUBLIC_KEYS"))
        assertTrue(s.contains("Release/production build için BORSA_ATTESTATION_PUBLIC_KEYS zorunludur"))
    }

    @Test fun outputHashHasExplicitV536Schema() {
        val c = source("src/main/java/tr/borsatakip/v5/analysis/v531/RemoteEngineContract.kt")
        val h = source("src/main/java/tr/borsatakip/v5/analysis/v533/V533AuditHash.kt")
        val schema = source("src/main/java/tr/borsatakip/v5/analysis/v536/V536CalculationOutputSchema.kt")
        assertTrue(c.contains("V540CalculationOutputSchema.SCHEMA"))
        assertTrue(c.contains("import tr.borsatakip.v5.analysis.v540.V540CalculationOutputSchema"))
        assertTrue(schema.contains("V536_REMOTE_OUTPUT_V1"))
        assertTrue(h.contains("outputSchema="))
        assertTrue(h.contains("qualityClass="))
        assertTrue(h.contains("marketRegime="))
        assertTrue(h.contains("spreadPct="))
    }

    @Test fun productionObservabilityAvoidsMarketPayload() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v536/V536Observability.kt")
        assertTrue(s.contains("no symbol, API key, payload, price or account data"))
        assertFalse(s.contains("val symbol:"))
        assertFalse(s.contains("val price:"))
    }

    @Test fun replayStoreIsProcessAtomicAndDurable() {
        val s = source("src/main/java/tr/borsatakip/v5/data/v538/V538ReplayStore.kt")
        assertTrue(s.contains("synchronized(PROCESS_LOCK)"))
        assertTrue(s.contains(".commit()"))
        assertTrue(s.contains("REPLAY_STATE_PERSIST_FAILED"))
        assertTrue(s.contains("MessageDigest.getInstance(\"SHA-256\")"))
    }

    @Test fun noPrivateKeyOrSharedSecretIsEmbedded() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v538/V538ServerAttestation.kt")
        assertTrue(s.contains("PublicKey"))
        assertFalse(s.contains("PrivateKey"))
        assertFalse(s.contains("SecretKey"))
    }
}
