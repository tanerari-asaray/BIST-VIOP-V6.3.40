package tr.borsatakip.v5.data

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FundamentalsEndpointDiagnosticTest {
    @Test fun missingRouteIsNotFinancialEvidence() {
        val msg = requireNotNull(FundamentalsEndpointDiagnostic.explain("NOT_FOUND: BIST temel finansallar HTTP 404 döndürdü."))
        assertTrue(msg.contains("API_ROUTE_NOT_FOUND"))
        assertTrue(msg.contains("/v1/bist/fundamentals"))
        assertTrue(msg.contains("değerleme yapılmadı"))
    }
    @Test fun upstream503IsNotSuccessfulValuation() {
        val msg = requireNotNull(FundamentalsEndpointDiagnostic.explain("SERVER_ERROR: BIST temel finansallar HTTP 503 döndürdü."))
        assertTrue(msg.contains("FINANCIAL_SOURCE_UNAVAILABLE"))
        assertTrue(msg.contains("değerleme yapılmadı"))
    }
    @Test fun otherErrorsRemainUnmodified() {
        assertNull(FundamentalsEndpointDiagnostic.explain("AUTH_ERROR: HTTP 401"))
        assertNull(FundamentalsEndpointDiagnostic.explain("NETWORK_TIMEOUT"))
        assertNull(FundamentalsEndpointDiagnostic.explain(""))
    }
}
