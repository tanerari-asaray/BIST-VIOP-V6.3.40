package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class StockQuoteHttpFailureRegressionTest {
    @Test fun disconnectedProviderIsNotAQuoteSchemaFailure() {
        val evidence = BackendHttpDiagnostic.fromResponse(503, "/v1/bist/quote/TEST",
            """{"code":"LICENSED_BIST_PROVIDER_NOT_CONNECTED"}""")
        assertEquals("LICENSED_BIST_PROVIDER_NOT_CONNECTED",
            StockQuotePolicy.failureCode(BackendHttpException(evidence, "HTTP 503")))
    }

    @Test fun missingQuoteRouteRetainsItsHttpStatus() {
        assertEquals("HTTP_404", StockQuotePolicy.failureCode(
            BackendHttpException(BackendHttpEvidence(httpStatus = 404), "HTTP 404")))
    }

    @Test fun providerAuthenticationFailureIsDistinctFromTheAppKey() {
        val evidence = BackendHttpDiagnostic.fromResponse(502, "/v1/bist/quote/TEST",
            """{"code":"BIST_UPSTREAM_HTTP_401","stage":"PROVIDER_AUTH","upstreamHttpStatus":401}""")
        assertEquals("BIST_UPSTREAM_HTTP_401", StockQuotePolicy.failureCode(
            BackendHttpException(evidence, "HTTP 502 • Sağlayıcı HTTP 401")))
    }
}
