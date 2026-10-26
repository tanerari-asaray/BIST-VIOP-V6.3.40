package tr.borsatakip.v5.data

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendUnavailableDiagnosticTest {
    @Test fun viop503IdentifiesAbsentLicensedFeedNotServerCrash() {
        val text = requireNotNull(BackendUnavailableDiagnostic.describe(503,"/v1/health",
            """{"ok":false,"detail":{"code":"LICENSED_VIOP_PROVIDER_NOT_CONNECTED"}}"""))
        assertTrue(text.contains("DATA_SOURCE_NOT_CONNECTED"))
        assertTrue(text.contains("lisanslı VİOP veri sağlayıcısı bağlı değil"))
        assertTrue(text.contains("tarama ve canlı sinyal kapalı"))
    }
    @Test fun bistSourceUnconfigured() {
        val text = requireNotNull(BackendUnavailableDiagnostic.describe(503,"/v1/bist/symbols",
            """{"detail":{"code":"LICENSED_BIST_PROVIDER_NOT_CONNECTED"}}"""))
        assertTrue(text.contains("Lisanslı BIST veri sağlayıcısı bağlı değil"))
    }
    @Test fun unknownServer503RemainsGeneric() {
        assertNull(BackendUnavailableDiagnostic.describe(503,"/v1/health", "{"))
        assertNull(BackendUnavailableDiagnostic.describe(503,"/v1/health", """{"detail":{"code":"UNKNOWN_503"}}"""))
        assertNull(BackendUnavailableDiagnostic.describe(502,"/v1/health", """{"detail":{"code":"LICENSED_VIOP_PROVIDER_NOT_CONNECTED"}}"""))
        assertNull(BackendUnavailableDiagnostic.describe(503,"/api/health", """{"detail":{"code":"LICENSED_VIOP_PROVIDER_NOT_CONNECTED"}}"""))
    }
    @Test fun malformedLongBodyRejected() {
        assertNull(BackendUnavailableDiagnostic.extractCode("{"+"A".repeat(4200)))
    }
    @Test fun financial503ExplainsMissingSource() {
        val msg = requireNotNull(FundamentalsEndpointDiagnostic.explain(
            "SERVER_ERROR: BIST temel finansallar HTTP 503 döndürdü. FINANCIAL_STATEMENTS_SOURCE_NOT_CONFIGURED"))
        assertTrue(msg.contains("FINANCIAL_SOURCE_NOT_CONNECTED"))
        assertTrue(msg.contains("BORSA_FUNDAMENTALS_UPSTREAM_URL"))
    }
}
