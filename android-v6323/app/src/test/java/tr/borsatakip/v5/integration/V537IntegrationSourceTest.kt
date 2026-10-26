package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V537IntegrationSourceTest {
    private fun source(path: String) = File(path).readText()

    @Test fun attestationUsesTrustedNowNotEnvelopeServerTime() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("trustedNowMillis = trustedNowMillis"))
        assertTrue(s.contains("V538TrustedTimeRegistry.now"))
        assertTrue(s.contains("parseHttpDateMillis(response.header(\"Date\"))"))
    }

    @Test fun replayUsesTrustedNowAndTimeframeSkew() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("trustedNowMillis = snapshot.trustedNowMillis"))
        assertTrue(s.contains("V536ClockPolicy.forTimeframe(snapshot.analysisTimeframeMinutes).futureToleranceMs"))
        assertFalse(s.contains("snapshot.serverTime\n        )"))
    }

    @Test fun serverEpochIsSignedAndRequired() {
        val client = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        val att = source("src/main/java/tr/borsatakip/v5/analysis/v537/V537ServerAttestation.kt")
        assertTrue(client.contains("root.optString(\"serverEpoch\")"))
        assertTrue(att.contains("serverEpoch="))
        assertTrue(att.contains("SERVER_EPOCH_REQUIRED"))
    }

    @Test fun sequenceIsScopedToServerEpoch() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v537/V537ReplayProtection.kt")
        assertTrue(s.contains("currentEpochFingerprint"))
        assertTrue(s.contains("SERVER_EPOCH_REPLAY"))
        assertTrue(s.contains("epochChanged"))
    }

    @Test fun replayEntriesAreTimestampedAndTtlPruned() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v537/V537ReplayProtection.kt")
        assertTrue(s.contains("data class V537ReplayEntry"))
        assertTrue(s.contains("acceptedAt >= cutoff"))
        assertFalse(s.contains("takeLast("))
    }

    @Test fun persistedReplayIdentifiersAreFingerprints() {
        val s = source("src/main/java/tr/borsatakip/v5/data/v537/V537ReplayStore.kt")
        assertTrue(s.contains("fingerprint"))
        assertTrue(s.contains("[0-9a-f]{64}"))
        assertTrue(s.contains("synchronized(PROCESS_LOCK)"))
        assertTrue(s.contains(".commit()"))
    }

    @Test fun observabilityIsProcessAtomicAndDurable() {
        val s = source("src/main/java/tr/borsatakip/v5/data/v537/V537ObservabilityStore.kt")
        assertTrue(s.contains("synchronized(OBSERVABILITY_LOCK)"))
        assertTrue(s.contains(".commit()"))
        assertFalse(s.contains(".apply()"))
    }

    @Test fun v537AttestationUsesExternalTrustedTimeWindow() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v537/V537ServerAttestation.kt")
        assertTrue(s.contains("trustedNowMillis"))
        assertTrue(s.contains("SIGNATURE_NOT_YET_VALID"))
        assertTrue(s.contains("SIGNATURE_EXPIRED"))
        assertFalse(s.contains("envelope.serverTime !in envelope.issuedAt..envelope.expiresAt") && !s.contains("trustedNowMillis"))
    }

    @Test fun websocketUsesTlsDateElapsedRealtimeBootstrap() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerSocket.kt")
        assertTrue(s.contains("response.header(\"Date\")"))
        assertTrue(s.contains("handshakeWallMillis + (elapsedNow - handshakeElapsedMillis)"))
        assertTrue(s.contains("parser.parse(root, bootstrapTrustedNow, elapsedNow)"))
    }

    @Test fun activeVersionIsAtLeastV538() {
        assertTrue(tr.borsatakip.v5.BuildConfig.VERSION_CODE >= 542)
        val build = source("build.gradle.kts")
        val scoring = source("src/main/java/tr/borsatakip/v5/analysis/v531/ScoringConfig.kt")
        assertTrue(build.contains("versionCode = ${tr.borsatakip.v5.BuildConfig.VERSION_CODE}"))
        assertTrue(build.contains("versionName = \"${tr.borsatakip.v5.BuildConfig.VERSION_NAME}\""))
        assertTrue(scoring.contains("ENGINE_VERSION = \"V5.4.1\""))
    }
}
