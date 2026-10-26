package tr.borsatakip.v5.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderProbePresentationTest {
    @Test fun noProbeIsNotARequestFailure() {
        val msg = ProviderProbePresentation.describe(ProviderReadinessSnapshot(ProviderState.PROVIDER_CONFIGURED))
        assertTrue(msg.contains("Alt adımlar: TEST EDİLMEDİ"))
        assertFalse(msg.contains("BAŞARISIZ"))
    }
    @Test fun noLicensedSourceMeansOnlyHealthFailed() {
        val msg = ProviderProbePresentation.describe(ProviderReadinessSnapshot(
            ProviderState.PROVIDER_ERROR, ProviderFailureCode.DATA_SOURCE_NOT_CONNECTED,
            "no upstream", testedAt = 12345L))
        assertTrue(msg.contains("HTTPS/Health: ✕ BAŞARISIZ"))
        assertTrue(msg.contains("BIST Symbols: — TEST EDİLMEDİ"))
        assertTrue(msg.contains("VİOP Contracts: — TEST EDİLMEDİ"))
        assertFalse(msg.contains("VİOP Contracts: ✕"))
    }
    @Test fun viopReadinessNeverClaimsBistTests() {
        val msg = ProviderProbePresentation.describe(ProviderReadinessSnapshot(
            ProviderState.PROVIDER_READY, healthOk = true, authOk = true,
            viopContractsOk = true, viopQuoteOk = true, viopHistoryOk = true,
            viopContractCount = 11, testedAt = 12345L))
        assertTrue(msg.contains("VİOP Quote: ✓ DOĞRULANDI"))
        assertTrue(msg.contains("BIST Quote: — TEST EDİLMEDİ"))
        assertTrue(msg.contains("VİOP History: ✓ 220 GÜNLÜK VERİ"))
    }
    @Test fun quoteCannotBeFailedWhenContractGateNotPassed() {
        val msg = ProviderProbePresentation.describe(ProviderReadinessSnapshot(
            ProviderState.PROVIDER_ERROR, healthOk = true, testedAt = 12345L))
        assertTrue(msg.contains("VİOP Contracts: ✕ DOĞRULANMADI"))
        assertTrue(msg.contains("VİOP Quote: — TEST EDİLMEDİ"))
        assertTrue(msg.contains("VİOP History: — TEST EDİLMEDİ"))
    }
    @Test fun quoteReadyButHistoricalCandlesInsufficientIsNotQuoteFailure() {
        val msg = ProviderProbePresentation.describe(ProviderReadinessSnapshot(
            ProviderState.PROVIDER_READY, healthOk = true, authOk = true,
            viopContractsOk = true, viopQuoteOk = true, viopHistoryOk = false,
            testedAt = 12345L))
        assertTrue(msg.contains("VİOP Quote: ✓ DOĞRULANDI"))
        assertTrue(msg.contains("ANALİZ GEÇMİŞİ YETERSİZ"))
    }
    @Test fun http503DoesNotMeanTheGatewayCouldNotBeReached() {
        val msg = ProviderProbePresentation.describe(ProviderReadinessSnapshot(
            ProviderState.PROVIDER_ERROR, ProviderFailureCode.DATA_SOURCE_NOT_CONNECTED,
            testedAt = 12345L,
            connection = BackendHttpEvidence(503, "/v1/health?market=VIOP",
                "LICENSED_VIOP_PROVIDER_NOT_CONNECTED", "PROVIDER_CONFIG", gatewayAuthenticated = true)))
        assertTrue(msg.contains("HTTP yanıtı alındı (503)"))
        assertTrue(msg.contains("Uygulama → sunucu kimlik doğrulama: DOĞRULANDI"))
        assertTrue(msg.contains("VİOP Quote: — TEST EDİLMEDİ"))
    }
    @Test fun noAuthEvidenceNeverAppearsAsAuthenticated() {
        val msg = ProviderProbePresentation.describe(ProviderReadinessSnapshot(
            ProviderState.PROVIDER_ERROR, testedAt = 12345L, connection = BackendHttpEvidence(503, "/v1/health")))
        assertTrue(msg.contains("AYRI SONUÇ KAYDI YOK"))
    }
}
