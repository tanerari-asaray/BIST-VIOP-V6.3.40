package tr.borsatakip.v5.integration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V538IntegrationSourceTest {
    private fun source(path: String) = File(path).readText()

    @Test fun realtimeUsesProviderScopedTrustedTime() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("V538TrustedTimeKey(providerId, attestationKeyId, attestationKeyGeneration)"))
        assertTrue(s.contains("V538TrustedTimeRegistry.now"))
        assertTrue(s.contains("V538TrustedTimeRegistry.markVerified"))
        assertFalse(s.contains("V537TrustedTimeAnchor.now"))
    }

    @Test fun providerKeyGenerationBindingIsRequired() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v538/V538ServerAttestation.kt")
        assertTrue(s.contains("providerId:keyId:keyGeneration:base64"))
        assertTrue(s.contains("PROVIDER_KEY_BINDING_NOT_TRUSTED"))
        assertTrue(s.contains("attestationKeyGeneration"))
    }

    @Test fun epochCarriesCreatedAtAndIsSigned() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v538/V538ServerAttestation.kt")
        assertTrue(s.contains("serverEpochCreatedAt="))
        assertTrue(s.contains("EPOCH_CREATED_AFTER_SIGNATURE_ISSUE"))
        assertTrue(s.contains("[A-Za-z0-9_-]{22,128}"))
    }

    @Test fun replayTokenContainsTimeEvidenceAndKeyScope() {
        val s = source("src/main/java/tr/borsatakip/v5/analysis/v538/V538ReplayProtection.kt")
        assertTrue(s.contains("val issuedAt: Long"))
        assertTrue(s.contains("val serverTime: Long"))
        assertTrue(s.contains("val attestationKeyGeneration: Long"))
        assertTrue(s.contains("SERVER_EPOCH_ROLLBACK"))
    }

    @Test fun replayStoreIsProviderKeyGenerationScoped() {
        val s = source("src/main/java/tr/borsatakip/v5/data/v538/V538ReplayStore.kt")
        assertTrue(s.contains("keyPrefix(token.providerId, token.attestationKeyId, token.attestationKeyGeneration)"))
        assertTrue(s.contains("highestEpochCreatedAt"))
        assertTrue(s.contains(".commit()"))
    }

    @Test fun restRequiresTlsDateBootstrap() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        assertTrue(s.contains("HTTPS Date başlığı olmadan trusted-time bootstrap yapılamaz"))
        assertFalse(s.contains("parseHttpDateMillis(response.header(\"Date\")) ?: clientReceiveWall"))
    }

    @Test fun websocketRequiresTlsDateBootstrap() {
        val s = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerSocket.kt")
        assertTrue(s.contains("trusted-time-bootstrap-missing"))
        assertTrue(s.contains("WebSocket HTTPS Date başlığı olmadan trusted-time bootstrap yapılamaz"))
    }

    @Test fun httpDateUsesRfc1123Parser() {
        val rest = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerClient.kt")
        val ws = source("src/main/java/tr/borsatakip/v5/data/RealtimeScannerSocket.kt")
        assertTrue(rest.contains("DateTimeFormatter.RFC_1123_DATE_TIME"))
        assertTrue(ws.contains("DateTimeFormatter.RFC_1123_DATE_TIME"))
        assertFalse(rest.contains("SimpleDateFormat"))
    }

    @Test fun releaseBuildRequiresProviderScopedKeyFormat() {
        val s = source("build.gradle.kts")
        assertTrue(s.contains("providerId:keyId:keyGeneration:base64PublicKey"))
    }

    @Test fun versionIs538() {
        assertTrue(tr.borsatakip.v5.BuildConfig.VERSION_CODE >= 542)
        val build = source("build.gradle.kts")
        val scoring = source("src/main/java/tr/borsatakip/v5/analysis/v531/ScoringConfig.kt")
        assertTrue(build.contains("versionCode = ${tr.borsatakip.v5.BuildConfig.VERSION_CODE}"))
        assertTrue(build.contains("versionName = \"${tr.borsatakip.v5.BuildConfig.VERSION_NAME}\""))
        assertTrue(scoring.contains("ENGINE_VERSION = \"V5.4.1\""))
    }
}
