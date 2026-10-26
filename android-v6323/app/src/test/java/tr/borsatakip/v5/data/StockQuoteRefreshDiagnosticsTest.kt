package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class StockQuoteRefreshDiagnosticsTest {
    private fun connection(url: String = "https://data.example", key: String = "TEST_ONLY") = BackendConnectionConfig(url, key, "test")

    @Test fun missingAddressIsDistinctFromMissingKey() {
        assertEquals("BACKEND_URL_MISSING", StockQuoteRefreshDiagnostics.configurationFailure(connection("", "")))
    }
    @Test fun invalidAddressCannotBeReportedAsAnEmptyQuote() {
        assertEquals("INVALID_HTTPS", StockQuoteRefreshDiagnostics.configurationFailure(connection("http://data.example")))
    }
    @Test fun freshInstallWithoutKeyHasAnExplicitLocalCause() {
        assertEquals("API_KEY_MISSING", StockQuoteRefreshDiagnostics.configurationFailure(connection(key = "  ")))
    }
    @Test fun validSetupDoesNotPretendThatTheProviderIsReady() {
        assertNull(StockQuoteRefreshDiagnostics.configurationFailure(connection()))
    }
    @Test fun sharedSetupFailureIsOneGroupWithAllAffectedSymbols() {
        val issues = (1..20).associate { "TEST$it" to "API_KEY_MISSING" }
        val groups = StockQuoteRefreshDiagnostics.groups(StockQuoteRefreshResult(emptyMap(), issues))
        assertEquals(1, groups.size); assertEquals(20, groups.single().symbols.size)
        assertEquals(issues.keys.sorted(), groups.single().symbols); assertNull(groups.single().httpEvidence)
    }
    @Test fun providerHttpFailuresGroupAcrossDifferentQuoteEndpoints() {
        val code = "LICENSED_BIST_PROVIDER_NOT_CONNECTED"
        val a = BackendHttpDiagnostic.fromResponse(503, "/v1/bist/quote/TESTA", """{"code":"$code"}""")
        val groups = StockQuoteRefreshDiagnostics.groups(StockQuoteRefreshResult(emptyMap(),
            mapOf("TESTA" to code, "TESTB" to code), mapOf("TESTA" to a, "TESTB" to a.copy(endpoint = "/v1/bist/quote/TESTB"))))
        val evidence = groups.single().httpEvidence!!
        assertEquals(503, evidence.httpStatus); assertEquals("PROVIDER_CONFIG", evidence.failureStage)
        assertEquals("", evidence.endpoint); assertEquals(2, groups.single().symbols.size)
    }
    @Test fun distinctHttpStagesRemainDistinctAndSuccessIsNotCountedAsFailure() {
        val groups = StockQuoteRefreshDiagnostics.groups(StockQuoteRefreshResult(emptyMap(),
            mapOf("TESTA" to "TIMEOUT", "TESTB" to "API_KEY_MISSING")))
        assertEquals(2, groups.size); assertEquals(setOf("TESTA", "TESTB"), groups.flatMap { it.symbols }.toSet())
        assertTrue(StockQuoteRefreshDiagnostics.groups(StockQuoteRefreshResult(emptyMap(), emptyMap())).isEmpty())
    }
    @Test fun arbitraryErrorTextIsNotCopiedIntoTheDialogCode() {
        val secret = "SECRET_TEST_ONLY"
        val groups = StockQuoteRefreshDiagnostics.groups(StockQuoteRefreshResult(emptyMap(), mapOf("TESTA" to "Bearer $secret")))
        assertEquals("NETWORK_OR_PROVIDER_ERROR", groups.single().code)
        assertFalse(groups.toString().contains(secret))
    }
    @Test fun unknownHttpErrorBodyCannotLeakThroughFailureCode() {
        val code = StockQuotePolicy.failureCode(BackendHttpException(
            BackendHttpEvidence(httpStatus = 503, providerCode = "SECRET_TEST_ONLY"), "Bearer SECRET_TEST_ONLY"))
        assertEquals("HTTP_503", code)
    }
}
